# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.10**, Java **21**, Gradle **9.4.1**.

Ported from Minecraft 1.21.1, which is the baseline the loader versions below were moved from, and
then from the finished 1.21.11 port, whose 1.21.11-only API usages were walked back to their 1.21.10
equivalents one at a time (every decision, with the `javap` evidence behind it, is in
[NOTES.md](NOTES.md)).

## Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: Loom 1.17.493
defaults to `mixinRemapType = static`, and fabric-api 0.138.4 - built with the same Loom - ships none
either and advertises `Fabric-Loom-Mixin-Remap-Type: static` in its manifest. The access widener in
the jar is in `v2 intermediary` (`v2 named` makes Fabric Loader 0.19.x abort the boot before the
game window exists).

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads: NeoForge 21.10 has no SRG step in front of a mod jar.
`accesstransformer.cfg` is written in the same naming. architectury-plugin's `neoForge()` transform is
therefore not used; it needs a Loom-based NeoForge setup, which cannot merge the Mojang and NeoForge
mappings.

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
./gradlew :common:build   # once on a fresh checkout
./gradlew build           # from then on
```

The two steps are needed because Architectury Loom resolves the `:common` project dependency while it
*configures* `:fabric`, before the root `build` task graph gets a chance to run. A single
`./gradlew build` on a clean checkout fails with
`Failed to read metadata from …/common/build/libs/parcool-1.21.10-3.4.3.3.jar`. This is inherited from
the Architectury plugin and is documented rather than worked around.

See [BUILDING.md](BUILDING.md) for the module layout, the dev run tasks
(`:fabric:runClient`, `:neoforge:runclient`, `:fabric:runServer`, `:neoforge:runserver`) and the
loader-independence check.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.10-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-neoforge.jar`

## Dependencies

**Required**

* Architectury API 18.0.8 or newer — the newest Architectury line that covers 1.21.10. 19.0.1 declares
  `minecraft: ~1.21.11` in its `fabric.mod.json` and Fabric Loader refuses to load it on 1.21.10.
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.138.4+1.21.10
* NeoForge 21.10.64 or newer
* MixinExtras 0.4+ — bundled by both loaders, declared as a required dependency in both mod
  descriptors because `common/…/mixin/common/PlayerMixin` uses `@WrapWithCondition` from it

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing)
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only. **EpicFight has
  no 1.21.10 build**, so the integration is compiled against its 1.21.1 API but reports itself absent
  at runtime (`EpicFightManager#isEpicFightUsable` checks the class is loadable) and ParCool falls
  back to its own stamina system.
* BetterThirdPerson — NeoForge only. The newest published build targets 1.21.8; it is compiled against
  and used the same way, and is reported absent on a client that does not have it.
* [Paragliders](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only. **CurseForge
  has no Paraglider build for 1.21.10** (the file list jumps from the 21.5.x line straight to
  21.11.0-beta), and the 21.11 build's bytecode references `net.minecraft.resources.Identifier`, which
  does not exist before 1.21.11. The integration is therefore compiled against **21.5.2**, whose
  plugin API is written against `ResourceLocation` and whose `MovementPlugin` interfaces
  `ParCoolPlugin` implements have the same signatures (verified with `javap` on both jars).

## Verification status

`./gradlew build`, `:common:checkCommonLoaderIndependence` and `tools/verify_mixins.py` all pass, and
both jars were inspected (contents, mapping namespace, access-widener namespace, absence of a
refmap, `pack.mcmeta` and the recipe JSON run through the real 1.21.10 codecs).

**The game has not been launched.** No `runClient`, `runServer` or Prism instance was used, so the
in-world behaviour — animations, the zipline render, the settings screen, two clients seeing each
other, the attribute resolving on the first `Player#createAttributes` — is unverified on 1.21.10 and
rests on the API-level evidence in NOTES.md. Read NOTES.md's §6 before trusting any of it.

> This repository contains a *port*, not alRex_U's original sources. The original project lives at
> <https://github.com/alRex-U/ParCool> and on CurseForge at
> <https://www.curseforge.com/minecraft/mc-mods/parcool>. All credit for the mod's design and assets
> goes to alRex_U; this repository only carries the loader-abstraction work.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
