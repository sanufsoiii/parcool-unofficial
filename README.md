# ParCool — Architectury API port (Minecraft 1.20.3)

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
Forge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **Forge** artifact.

Minecraft **1.20.3**, Java **17**.

> This repository contains a *port*, not alRex_U's original sources. The original project lives at
> <https://github.com/alRex-U/ParCool> and on CurseForge at
> <https://www.curseforge.com/minecraft/mc-mods/parcool>. All credit for the mod's design and assets
> goes to alRex_U; this repository only carries the loader-abstraction work.

## Modules

| Module   | Contents                                                                                                                                                                       |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common` | The whole mod: registries, `ParCoolData` (replacement for NeoForge's attachment API), the network layer, the JSON config backend, the vanilla mixins, and the platform seam.    |
| `fabric` | Fabric entrypoints, `FabricParCoolPlatform`, and the ServiceLoader binding.                                                                                                     |
| `forge`  | Forge entrypoint (`@Mod`), `ForgeParCoolPlatform`, plus the optional Paraglider, EpicFight, BetterThirdPerson and ShoulderSurfing integrations. |

There is intentionally **no source-set split**: ParCool is client-authoritative, and both loaders
ship a merged jar, so `common` stays a single tree. The two loader modules differ only in their
entrypoints and their platform implementation.

## Requirements

* JDK 17 (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path needed)

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~15 s
./gradlew build           # from then on
```

See [BUILDING.md](BUILDING.md) for the module layout, the 1.21.1 → 1.20.1 → 1.20.4 → 1.20.3 port
notes (this tree is the 1.20.4 port retargeted down one release — 1.20.3 shares 1.20.4's API, so only
metadata and comments moved; Forge targets SRG names, so the distributable is assembled by
`:forge:remapJar`, the Forge dev run has to load a single `parcool` JPMS module, Forge 49's bootstrap
needs the LWJGL modules Loom splits off filtered out of `runServer`, and 49.0.2's own bootstrap 2.0.0
is replaced by the 2.1.8 launcher for dev runs), the dev run tasks (`:fabric:runClient`,
`:forge:runClient`, `:fabric:runServer`, `:forge:runServer`), the mixin target verifier
(`tools/verify_mixins.py`) and the loader-independence check.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.20.3-3.4.3.3-fabric.jar`
* Forge — `forge/build/libs/parcool-1.20.3-3.4.3.3-forge.jar`

## Dependencies

**Required**

* Architectury API 11.1.17 or newer (the 1.20.3/1.20.4 line)
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.91.1+1.20.3

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing) — the 1.20.3-era
  4.2.x builds work; only the camera-coupling part of the integration needs the newer 4.11.0 API, and
  it degrades cleanly (see BUILDING.md)
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — Forge only
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — Forge only
* BetterThirdPerson — Forge only

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
