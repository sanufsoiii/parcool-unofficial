# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.5**, Java **21**, Gradle **9.4.1**.

Ported from Minecraft 1.21.1, which is the baseline the loader versions below were moved from, and
built on the finished 1.21.4 port of this same mod. The port is functionally complete on both loaders:
29 vanilla mixins, 26 actions, the zipline/hook blocks and the full animation set all work, and the
four optional NeoForge integrations are wired.

### Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: Loom 1.17
defaults to `mixinRemapType = static`, and fabric-api 0.128.2 — built with the same Loom — ships none
either and advertises `Fabric-Loom-Mixin-Remap-Type: static` in its manifest.

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads: NeoForge 21.5 has no SRG step in front of a mod jar. `accesstransformer.cfg` is written
in the same naming. architectury-plugin's `neoForge()` transform is therefore not used; it needs a
Loom-based NeoForge setup, which cannot merge the Mojang and NeoForge mappings.

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

* JDK 21 (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path needed)

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~20 s
./gradlew build           # from then on
```

The two steps are needed because Architectury Loom resolves the `:common` project dependency while it
*configures* the loader modules, so `:common` has to have been built once. See
[BUILDING.md](BUILDING.md) for the module layout, the dev run tasks
(`:fabric:runClient`, `:neoforge:runclient`, `:fabric:runServer`, `:neoforge:runserver`) and the
loader-independence check.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.5-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-neoforge.jar`

## Dependencies

**Required**

* Architectury API 16.1.4 or newer
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.128.2+1.21.5 (Fabric Loader 0.16.14+)
* NeoForge 21.5.98 or newer
* MixinExtras 0.4+ — bundled by both loaders, declared as a required dependency in both mod
  descriptors because `common/…/mixin/common/PlayerMixin` uses `@WrapWithCondition` from it

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing) — NeoForge only.
  ShoulderSurfing publishes **no** 1.21.5 build, so the integration is compiled against its 1.21.4
  build and reports itself absent at runtime if the installed version's classes do not match.
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only. **EpicFight has
  no 1.21.5 build**, so the integration is compiled against its 1.21.1 API but reports itself absent at
  runtime (`EpicFightManager#isEpicFightUsable` checks the class is loadable) and ParCool falls back to
  its own stamina system.
* BetterThirdPerson — NeoForge only, built for 1.21.5 and up.
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only. Upstream
  publishes a 1.21.5 build, but the CurseForge Maven proxy this machine can reach no longer serves
  any file, so the integration is compiled against the 1.21.1 build; the API ParCool touches
  (`ParagliderItemCapability`, `Stamina`) is unchanged across those builds.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to the
port itself are welcome under the same terms.
