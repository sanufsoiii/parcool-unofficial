# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.8**, Java **21**, Gradle **9.4.1**.

1.21.8 is a bugfix release on top of 1.21.7, so the source is a *hybrid* of the two API sides: the
1.21.1 API where 1.21.8 still has it (`ResourceLocation`, one `KeyMapping` per physical key,
string recipe ingredients, a `String` key category) and the newer API where 1.21.8 already has it
(`ValueInput`/`ValueOutput` saves, the split `Entity#hurt`, `jumpFromGround` on `LivingEntity`,
`ItemTintSources`, `BlockItem#getDescriptionId` gone, the `Item.Properties#setId` requirement). Each
of those seams is marked in the comment where it matters.

The port is complete on both loaders: 27 vanilla mixins, 26 actions, the zipline/hook blocks, the
full animation set and the four optional NeoForge integrations are all wired and working. Both loaders
have been run in a world and in a real Prism instance — see [BUILDING.md](BUILDING.md) for the
item-by-item breakdown and for what is still uncovered.

### Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: Loom 1.17
defaults to `mixinRemapType = static` and no longer runs the legacy mixin annotation processor, and
fabric-api — built with the same Loom — ships none either and advertises
`Fabric-Loom-Mixin-Remap-Type: static` in its manifest. The access widener travels in `v2
intermediary`, because Fabric Loader refuses a `v2 named` one on a production client.

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads: NeoForge 21.8 has no SRG step in front of a mod jar. `accesstransformer.cfg` is
written in the same naming. architectury-plugin's `neoForge()` transform is therefore not used; it
needs a Loom-based NeoForge setup, which cannot merge the Mojang and NeoForge mappings.

**Known limitation:** the ShoulderSurfing integration is NeoForge-only in practice. The
`shouldersurfing_plugin.json` in the Fabric jar is read by ShoulderSurfing's own loader, which it
only ships for NeoForge, so on Fabric the decoupled-camera hook is absent rather than broken.

On NeoForge, Architectury 17.0.8 additionally prints a "Warning while loading mods" dialog about
`@OnlyIn` at launch. It comes from that dependency, not from ParCool, and is harmless.

> This repository contains a *port*, not alRex_U's original sources. The original project lives at
> <https://github.com/alRex-U/ParCool> and on CurseForge at
> <https://www.curseforge.com/minecraft/mc-mods/parcool>. All credit for the mod's design and assets
> goes to alRex_U; this repository only carries the loader-abstraction work.

## Modules

| Module     | Contents                                                                                                                                                        |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`   | The whole mod: registries, `ParCoolData` (replacement for NeoForge's attachment API), the network layer, the JSON config backend, the vanilla mixins, and the platform seam. |
| `fabric`   | Fabric entrypoints, `FabricParCoolPlatform` / `FabricParCoolNetwork`, and the ServiceLoader binding.                                                                |
| `neoforge` | NeoForge entrypoints, `NeoForgeParCoolPlatform` / `NeoForgeParCoolNetwork`, `NeoForgeAttributes`, plus the optional Paraglider, EpicFight, BetterThirdPerson and ShoulderSurfing integrations. |

There is intentionally **no source-set split**: ParCool is client-authoritative, and NeoForge ships a
merged jar, so `common` stays a single tree. The two loader modules differ only in their entrypoints
and their platform implementation. `./gradlew :common:checkCommonLoaderIndependence` enforces that.

## Requirements

* JDK 21 or newer (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path
  needed)
* Minecraft 1.21.8

## Building

```bash
./gradlew :common:build   # once on a fresh checkout
./gradlew build           # from then on
```

See [BUILDING.md](BUILDING.md) for the module layout, the verification tools under `tools/`, the dev
run tasks (`:fabric:runClient`, `:neoforge:runclient`, `:fabric:runServer`, `:neoforge:runserver`),
the loader-independence check and the list of what has and has not been exercised in game.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.8-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-1.21.8-3.4.3.3-neoforge.jar`

## Dependencies

**Required**

* Architectury API 17.0.8 or newer. This is the architectury line built for the 1.21.7 / 1.21.8
  releases; 18.x targets 1.21.9+ and 19.0.1 targets 1.21.11.
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.136.1+1.21.8
* NeoForge 21.8.54 or newer
* MixinExtras 0.4+ — bundled by both loaders, so it is declared as an *optional* dependency in both
  mod descriptors (`custom.mixinextras.required: false` on Fabric, `type = "optional"` on NeoForge);
  `common/…/mixin/common/PlayerMixin` does use `@WrapWithCondition` from it. Declaring it required
  would make NeoForge refuse to start, because the loaders bundle it rather than listing it as a mod.

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing) — NeoForge only
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only. **EpicFight has
  no 1.21.8 build** (its 1.21.1 line is still the latest release), so the integration is compiled
  against that API but reports itself absent at runtime (`EpicFightManager#isEpicFightUsable` checks
  the class is loadable) and ParCool falls back to its own stamina system.
* [BetterThirdPerson](https://www.curseforge.com/minecraft/mc-mods/betterthirdperson) — NeoForge only,
  the newest published build targets 1.21.8; compiled against and used the same way, and reported
  absent on a client that does not have it.
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only. Paraglider
  publishes no 1.21.8 build, so the integration is compiled against its 1.21.5 build, the newest one
  still on the `ResourceLocation` side of its plugin API; it stays optional at runtime.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
