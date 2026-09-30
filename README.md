# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.7**, Java **21**, Gradle **9.4.1**.

The port is complete on both loaders: 27 vanilla mixins, 26 actions, the zipline/hook blocks and the
full animation set are all present and working, and the four optional NeoForge integrations are wired.
Both loaders have been run in a world and in a real Prism instance — see
[BUILDING.md](BUILDING.md) for the item-by-item breakdown and for what is still uncovered.

### Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: Loom 1.17
defaults to `mixinRemapType = static`, and fabric-api 0.128.2 — built with the same Loom — ships none
either and advertises `Fabric-Loom-Mixin-Remap-Type: static` in its manifest. Its access widener is
`v2 intermediary`; the copy `:common:remapJar` leaves alone is `v2 named` and is dropped, because
Fabric Loader 0.16 refuses it on a production client
(`ClassTweakerFormatException: Namespace (named) does not match current runtime namespace`).

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads: NeoForge 21.7 has no SRG step in front of a mod jar. `accesstransformer.cfg` is written
in the same naming. architectury-plugin's `neoForge()` transform is therefore not used; it needs a
Loom-based NeoForge setup, which cannot merge the Mojang and NeoForge mappings.

**Known limitations:**

* EpicFight has no 1.21.7 build, so `extern/epicfight` is compiled against its 1.21.1 API
  (`21.15.1`) and reports itself absent at runtime (`EpicFightManager#isEpicFightUsable` checks that
  the class is loadable); ParCool falls back to its own stamina system.
* Paraglider has no 1.21.6/1.21.7 build. The integration is compiled against the 1.21.5 build
  (`21.5.2`), the newest one still on the `ResourceLocation` side of its plugin API — the 1.21.11 build
  cannot be used at all, because its own bytecode references `net.minecraft.resources.Identifier`.
* BetterThirdPerson is NeoForge only; the newest published build targets 1.21.8. It is compiled against
  and used the same way, and is reported absent on a client that does not have it.
* The ShoulderSurfing integration is NeoForge-only in practice: the `shouldersurfing_plugin.json` in
  the Fabric jar is never read on Fabric, so the decoupled-camera hook is absent there rather than
  broken.

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
./gradlew :common:build   # once on a fresh checkout
./gradlew build           # from then on
```

See [BUILDING.md](BUILDING.md) for the module layout, the run tasks
(`:fabric:runClient`, `:neoforge:runclient`, `:fabric:runServer`, `:neoforge:runserver`), the
loader-independence check and the list of what has and has not been exercised in game.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.7-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-neoforge.jar`

## Dependencies

**Required**

* Architectury API 17.0.8 or newer. 18.0.8 declares `~1.21.7` too, but it asks for a fabric-api built
  for 1.21.10, which Fabric Loader rejects at resolution time on 1.21.7. The full comparison is in
  `gradle.properties`.
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.128.2+1.21.7
* NeoForge 21.7.25-beta or newer
* MixinExtras 0.4+ — bundled by both loaders, so it is declared as an *optional* dependency in both
  mod descriptors (`custom.mixinextras.required: false` on Fabric, `type = "optional"` on NeoForge);
  `common/…/mixin/common/PlayerMixin` does use `@WrapWithCondition` from it. Declaring it required
  would make NeoForge refuse to start, because the loaders bundle it rather than listing it as a mod.

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing)
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only
* BetterThirdPerson — NeoForge only
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only, built against
  the 1.21.5 line

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
