# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.9**, Java **21**, Gradle **9.4.1**.

The port is complete on both loaders: 28 vanilla mixins, 26 actions, the zipline/hook blocks and the
full animation set are all present and working. Both loaders have been run in a world and in a real
Prism instance — see [Status](#status) for exactly what that covered and what it did not.

### Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: Loom 1.17
defaults to `mixinRemapType = static`, and the mod's own access widener is shipped in `v2 intermediary`
(`v2 named` makes Fabric Loader 0.19.x abort the boot before the window exists).

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads. Verified against a shipped NeoForge mod (Paraglider 21.5.2), whose own
`accesstransformer.cfg` names members the same way rather than with SRG tokens, and against the
21.9.16-beta runtime artifact itself. architectury-plugin's `neoForge()` transform is therefore not
used; it needs a Loom-based NeoForge setup, which cannot merge the Mojang and NeoForge mappings, so
`:neoforge` assembles the distributable explicitly.

> This repository contains a *port*, not alRex_U's original sources. The original project lives at
> <https://github.com/alRex-U/ParCool> and on CurseForge at
> <https://www.curseforge.com/minecraft/mc-mods/parcool>. All credit for the mod's design and assets
> goes to alRex_U; this repository only carries the loader-abstraction work.

## Status

`./gradlew build` succeeds and `checkCommonLoaderIndependence` passes.

Checked in a running game, on both Fabric and NeoForge: the loaders boot with the mod loaded, the two
ParCool attributes resolve on the first `Player#createAttributes`, parkour and the zipline (including
the rope render) work, the stamina HUD and the settings screen behave, the rope takes its dye colour,
key bindings can be rebound, and progress survives saving and reloading a world. Both jars were also
installed into a real Prism instance.

Not covered: a dedicated NeoForge server, a cross-loader join (NeoForge client against Fabric server)
and two players in one world.

Statically, the packaging was also checked before any of that: every `@Mixin` / `@Inject` /
`@Redirect` / `@WrapWithCondition` target resolves in the 1.21.9 mojmap jar with the exact
descriptor, every injection handler's parameter count matches its target, every target *as remapped
into the shipped Fabric jar* resolves in the 1.21.9 intermediary jar, and every entry of the shipped
access widener names a member that exists.

## Modules

| Module     | Contents                                                                                                                                                        |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`   | The whole mod: registries, `ParCoolData` (replacement for NeoForge's attachment API), the network layer, the JSON config backend, the vanilla mixins, and the platform seam. |
| `fabric`   | Fabric entrypoints, `FabricParCoolPlatform` / `FabricParCoolNetwork`, and the ServiceLoader binding.                                                                |
| `neoforge` | NeoForge entrypoints, `NeoForgeParCoolPlatform` / `NeoForgeParCoolNetwork`, plus the optional Paraglider, EpicFight, BetterThirdPerson and ShoulderSurfing integrations. |

There is intentionally **no source-set split**: ParCool is client-authoritative, and NeoForge ships a
merged jar, so `common` stays a single tree. The two loader modules differ only in their entrypoints
and their platform implementation.

## Requirements

* JDK 21 or newer (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path
  needed)
* Architectury API **18.0.5** — see the note below, it is not interchangeable with the 19.x line

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

* Fabric — `fabric/build/libs/parcool-1.21.9-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-neoforge.jar`

## Dependencies

**Required**

* Architectury API 18.0.5 — the 18.x line is the one whose `fabric.mod.json` accepts 1.21.9
  (`depends.minecraft = "~1.21.7"`, i.e. `>=1.21.7 <1.22.0`). The 19.x line declares `~1.21.11` and
  **refuses** 1.21.9 outright. Architectury published nothing between 1.21.7 and 1.21.11, so 1.21.8,
  1.21.9 and 1.21.10 all fall into the 18.x window. 18.0.5 rather than 18.0.8 because 18.0.6+ drags
  in a fabric-api that hard-requires 1.21.10, which Fabric Loader rejects here at resolution time;
  the full comparison is in `gradle.properties`.
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.134.1+1.21.9
* NeoForge 21.9.16-beta or newer — note that **every published NeoForge build for 1.21.9 carries the
  `-beta` suffix**; there is no non-beta release for this Minecraft version
* MixinExtras 0.4+ — bundled by both loaders, so it is declared as an *optional* dependency in both
  mod descriptors (`custom.mixinextras.required: false` on Fabric, `type = "optional"` on NeoForge);
  `common/…/mixin/common/PlayerMixin` does use `@WrapWithCondition` from it. Declaring it required
  would make NeoForge refuse to start, because the loaders bundle it rather than listing it as a mod.

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing) — NeoForge only
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only
* BetterThirdPerson — NeoForge only
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only

**All four optional integrations are inert on 1.21.9.** None of them publishes a build that declares
this Minecraft version, so a 1.21.9 client cannot install one. They are compiled against the newest
build whose API is compatible (Paraglider 21.5.2 / MC 1.21.5, ShoulderSurfing 4.11.0 / MC 1.21.1,
BetterThirdPerson 1.9.0 / MC 1.21.5–1.21.8, EpicFight 21.15.1 / MC 1.21.1) and ParCool reports itself
absent at runtime, falling back to its own stamina system and vanilla camera behaviour. The exact
coordinates each integration is compiled against are listed in `neoforge/build.gradle`.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
