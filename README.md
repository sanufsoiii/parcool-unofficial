# ParCool — Architectury API port (Minecraft 1.21.3)

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.3**, Java **21**, Gradle **8.10.2**.

The port is complete on both loaders: 28 vanilla mixins, 26 actions, the zipline/hook blocks, the
full animation set and the optional NeoForge integrations are all wired against the real 1.21.3 and
NeoForge 21.3.97 APIs.

Both loaders have been run in a world and in a real Prism instance; see [Status](#status) for exactly
what that covered and what it did not.

### Packaging

The Fabric distributable is built from `:fabric`'s own `remapJar`, expanded into the jar by
`distJar`, plus `:common`'s `remapJar` with its copy of the access widener excluded. So the jar
carries intermediary bytecode, a `v2 intermediary` access widener, and **no refmap** — Loom 1.7
rewrites the mixin targets straight into the bytecode because `useLegacyMixinAp = false` is set
explicitly in `common/build.gradle` (Loom 1.7 defaults to the refmap flow, which cannot work for
ParCool: its mixin targets are class literals, so the generated refmap would have no class entries
and nothing would read it). Ordering matters because `distJar` uses `duplicatesStrategy = EXCLUDE`:
the first `from { }` that contributes an entry wins, and `:fabric:remapJar` is the only one whose
access widener is remapped.

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads — verified for 21.3.97 against the `neoforge-21.3.97-sources.jar` ModDevGradle compiles
against, and against a shipped NeoForge mod's own `accesstransformer.cfg` (`Paraglider-neoforge-21.1.5`
lists `aboveGroundTickCount`, not an `f_110147_` token). architectury-plugin's `neoForge()` transform
is therefore not used; `:neoforge` assembles the distributable explicitly from
`:common`'s `transformProductionNeoForge` artifact. Its `META-INF/accesstransformer.cfg` is written in
the same mojmap naming — an `f_...` entry there would never match anything.

**Known limitations, both inherited from the 1.21.1 branch and unchanged by the version delta:**

* The **ShoulderSurfing** integration is NeoForge-shaped in practice. ShoulderSurfing's
  `ICameraCouplingCallback` / `IShoulderSurfing#isCameraDecoupled()` arrived in 4.7.0, and 4.6.3 is the
  newest build CurseForge published for 1.21.2/1.21.3. The integration is re-expressed on the
  free-look target offset that 4.6.3 does expose. The 5.x line is 1.21.1/1.21.11 only, and on a
  client that has none of these builds the integration reports itself absent rather than throwing.
* **EpicFight** has no 1.21.3 build, and neither does **Paraglider** (Paraglider's file list jumps
  1.21.1 → 1.21.5). Both are compiled against their 1.21.1 jars and guarded by a class-loadability
  check, so a matching install works and no install means "absent".

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
and their platform implementation. One `parcool-common.mixins.json` and one
`parcool.accesswidener` are shipped by both jars.

## Requirements

* JDK 21 (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path needed)

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~15 s
./gradlew build           # from then on
```

See [BUILDING.md](BUILDING.md) for the module layout, the dev run tasks
(`:fabric:runClient`, `:neoforge:runclient`, `:fabric:runServer`, `:neoforge:runserver`) and the
loader-independence check.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.3-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-1.21.3-3.4.3.3-neoforge.jar`

## Dependencies

**Required**

* Architectury API 14.0.4 or newer
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.114.1+1.21.3, Fabric Loader 0.16.10
* NeoForge 21.3.97 or newer
* MixinExtras 0.4+ — bundled by both loaders, declared as an optional dependency in both mod
  descriptors because `common/…/mixin/common/PlayerMixin` uses `@WrapWithCondition` from it and the
  loaders never list it as a mod

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing) — NeoForge shaped,
  see above
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only, no 1.21.3 build
* BetterThirdPerson — NeoForge only
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only, no 1.21.3
  build

## Status

`./gradlew build` succeeds and `checkCommonLoaderIndependence` passes.

Checked in a running game, on both Fabric and NeoForge: the loaders boot with the mod loaded, the two
ParCool attributes resolve on the first `Player#createAttributes`, parkour and the zipline (including
the rope render) work, the stamina HUD and the settings screen behave, the rope takes its dye colour,
key bindings can be rebound, and progress survives saving and reloading a world. Both jars were also
installed into a real Prism instance.

Not covered: a dedicated NeoForge server, a cross-loader join (NeoForge client against Fabric server)
and two players in one world.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
