# ParCool — Architectury API port (Minecraft 1.21.6)

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.6**, Java **21**, Gradle **9.4.1**.

The mod itself is complete on both loaders: 29 vanilla mixins, 26 actions, the zipline/hook blocks and
the full animation set, plus the four optional NeoForge integrations. Both loaders have been run in a
world and in a real Prism instance — see [BUILDING.md](BUILDING.md) for the item-by-item breakdown and
for what is still uncovered.

### 1.21.6 is a hybrid version

Worth knowing before reading the source, because it is the reason several files look "out of date"
next to the newer branches:

* **new side** — `Registry#get` returns an `Optional`, entity/block-entity saving is
  `ValueInput`/`ValueOutput`, `Entity#hurt` is a `final void` with `hurtOrSimulate`/`hurtServer`,
  `jumpFromGround` lives on `LivingEntity`, `BlockEntityType.Builder` is gone, `Item.Properties#setId`
  is mandatory, `InteractionResult` is an interface, item models live in `assets/<ns>/items/*.json`,
  and **entity rendering is render-state based** (`PlayerRenderState`, `extractRenderState`).
* **old side** — `ResourceLocation` (not `Identifier`), `KeyMapping`'s category is still a `String`
  and `KeyMapping.MAP` still holds **one mapping per physical key**, `Screen`'s input methods are the
  plain `keyPressed(int,int,int)` / `mouseClicked(double,double,int)` pair, and the **render type
  layer is still the `RenderStateShard` model** (`RenderSetup` only arrives in 1.21.9).
* **its own thing** — recipe ingredients are the *string* form (`"minecraft:chain"`,
  `"#minecraft:logs"`), like 1.21.1, but the recipe `result` object is the new `{"count", "id"}`
  form. `common/src/main/resources/data/parcool/recipe/*` follows exactly that.

`common/src/main/resources/parcool.accesswidener` and the module `build.gradle` files record the rest,
one line per seam.

### Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: Loom 1.17
defaults to `mixinRemapType = static`, and fabric-api — built with the same Loom — ships none either
and advertises `Fabric-Loom-Mixin-Remap-Type: static` in its manifest.

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads: NeoForge 21.6 has no SRG step in front of a mod jar. `accesstransformer.cfg` is
written in the same naming. architectury-plugin's `neoForge()` transform is therefore not used; it
needs a Loom-based NeoForge setup, which cannot merge the Mojang and NeoForge mappings.

**Known limitation:** the ShoulderSurfing integration is NeoForge-only in practice. ShoulderSurfing
publishes a `PluginLoader` for NeoForge and none for Fabric, so the `shouldersurfing_plugin.json` in
the Fabric jar is simply never read. The decoupled-camera hook is therefore absent on Fabric rather
than broken there.

> This repository contains a *port*, not alRex_U's original sources. The original project lives at
> <https://github.com/alRex-U/ParCool> and on CurseForge at
> <https://www.curseforge.com/minecraft/mc-mods/parcool>. All credit for the mod's design and assets
> goes to alRex_U; this repository only carries the loader-abstraction work.

## Modules

| Module     | Contents                                                                                                                                                        |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`   | The whole mod: registries, `ParCoolData` (replacement for NeoForge's attachment API), the network layer, the JSON config backend, the vanilla mixins, and the platform seam. |
| `fabric`   | Fabric entrypoints, `FabricParCoolPlatform` / `FabricParCoolNetwork`, and the ServiceLoader binding.                                                                |
| `neoforge` | NeoForge entrypoints, `NeoForgeParCoolPlatform` / `NeoForgeParCoolNetwork`, plus the optional Paraglider, EpicFight and BetterThirdPerson integrations.           |

There is intentionally **no source-set split**: ParCool is client-authoritative, and NeoForge ships a
merged jar, so `common` stays a single tree. The two loader modules differ only in their entrypoints
and their platform implementation.

## Requirements

* JDK 21 or newer (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path
  needed)

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~40 s
./gradlew build           # from then on
```

See [BUILDING.md](BUILDING.md) for the module layout, the dev run tasks, the loader-independence and
mixin-target checks, and the list of what has and has not been exercised in game.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.6-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-1.21.6-3.4.3.3-neoforge.jar`

## Dependencies

**Required**

* Architectury API 17.0.6 or newer
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.128.2+1.21.6
* NeoForge 21.6.20-beta or newer (the 1.21.6 line shipped no NeoForge stable)
* MixinExtras 0.4+ — bundled by both loaders, so it is declared as an *optional* dependency in both
  mod descriptors (`custom.mixinextras.required: false` on Fabric, `type = "optional"` on NeoForge);
  `common/…/mixin/common/PlayerMixin` does use `@WrapWithCondition` from it. Declaring it required
  would make NeoForge refuse to start, because the loaders bundle it rather than listing it as a mod.

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing)
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only. **EpicFight has
  no 1.21.6 build** (its 1.21.1 line is still the latest release), so the integration is compiled
  against that and reports itself absent at runtime (`EpicFightManager#isEpicFightUsable` checks the
  class is loadable); ParCool falls back to its own stamina system.
* BetterThirdPerson — NeoForge only. Built against the 1.21.5 release, which declares 1.21.6
  support; reported absent on a client that does not have it.
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only. **Paraglider
  has no 1.21.6 build**, so it is compiled against the 1.21.5 line (`21.5.2`) and reported absent at
  runtime on a client without it.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to the
port itself are welcome under the same terms.
