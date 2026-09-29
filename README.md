# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.2**, Java **21**, Gradle **8.10.2**.

Ported from the 1.21.11 Architectury tree downwards, with the 1.21.1 tree as the source for
everything 1.21.2 still spells the old way. The port is functionally complete on both loaders: 28
vanilla mixins, 26 actions, the zipline/hook blocks and the full animation set, and the four optional
NeoForge integrations are wired.

> **1.21.2 is a hybrid version.** It already has the entity render-state rework, `Registry#get`
> returning an `Optional`, `Entity#hurt` returning `void`, the `ClientInput#keyPresses` record, the
> `ClientLevel#addDestroyBlockEffect` hook and `Minecraft#getDeltaTracker` — but it still has
> `ResourceLocation`, `RenderStateShard` composites, a single-mapping-per-key `KeyMapping.MAP` and
> `CompoundTag` entity saves. Both of the reference trees are therefore wrong in one direction or the
> other for parts of this mod. **[NOTES.md](NOTES.md) §2 has the full table**, derived from the jar
> rather than from a version number, and it is the first thing to read before changing anything here.

### Packaging

The Fabric distributable is built from `:common`'s `remapJar` output, so it ships intermediary
bytecode and the mixin targets remapped in place. There is deliberately **no refmap**: this tree sets
`useLegacyMixinAp = false` (Loom 1.7's default is the legacy refmap flow, which leaves the mixin
targets in mojmap inside the constant pool and produces a jar that dies during mixin application on a
production client). The access widener must ship in `v2 intermediary` — `:common:remapJar` copies it
verbatim because its namespaces match, so the authoritative copy comes from `:fabric:remapJar` and the
common one is excluded. See `fabric/build.gradle` for the full reasoning.

The NeoForge distributable ships mojmap-named bytecode, because that is what the NeoForge production
runtime loads: NeoForge 21.2 has no SRG step in front of a mod jar. `accesstransformer.cfg` is written
in the same naming, verified against a shipped NeoForge mod (Paraglider 21.1.3), whose own AT ships
`aboveGroundTickCount` rather than an `f_110147_` token. architectury-plugin's `neoForge()` transform
is therefore not used; it needs a Loom-based NeoForge setup that cannot merge the Mojang and NeoForge
mappings, so `:neoforge` assembles the distributable explicitly and consumes `:common`'s
`transformProductionNeoForge` artifact as a file dependency.

**Known limitation:** the ShoulderSurfing integration is NeoForge-only in practice. A 1.21.2 build of
ShoulderSurfing is not published, and the `shouldersurfing_plugin.json` in the Fabric jar is read only
when a Fabric PluginLoader for it is installed. The decoupled-camera hook is therefore absent on
Fabric rather than broken there. The same applies to Paraglider, EpicFight and BetterThirdPerson,
which have no 1.21.2 build at all; all four are compiled against their newest 1.21.1-line build and
report themselves absent at runtime otherwise (see NOTES.md §4).

> This repository contains a *port*, not alRex_U's original sources. The original project lives at
> <https://github.com/alRex-U/ParCool> and on CurseForge at
> <https://www.curseforge.com/minecraft/mc-mods/parcool>. All credit for the mod's design and assets
> goes to alRex_U; this repository only carries the loader-abstraction work.

## Modules

| Module     | Contents                                                                                                                                                        |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`   | The whole mod: registries, `ParCoolData` (replacement for NeoForge's attachment API), the network layer, the JSON config backend, the vanilla mixins, and the platform seam. |
| `fabric`   | Fabric entrypoints, `FabricParCoolPlatform` / `FabricParCoolNetwork`, and the ServiceLoader binding.                                                                |
| `neoforge` | NeoForge entrypoints, `NeoForgeParCoolPlatform` / `NeoForgeParCoolNetwork`, `NeoForgeAttributes`, plus the optional Paraglider, EpicFight and BetterThirdPerson integrations. |

There is intentionally **no source-set split**: ParCool is client-authoritative, and NeoForge ships a
merged jar, so `common` stays a single tree. The two loader modules differ only in their entrypoints
and their platform implementation.

## Requirements

* JDK 21 (pinned through the Gradle toolchain in `build.gradle`, no machine-specific path needed)

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~15 s
./gradlew build           # from then on
```

See [BUILDING.md](BUILDING.md) for the module layout, the dev run tasks
(`:fabric:runClient`, `:neoforge:runclient`, `:fabric:runServer`, `:neoforge:runserver`) and the
loader-independence check, and [NOTES.md](NOTES.md) for the version deltas and the known dead ends.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.2-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-neoforge.jar`

## Dependencies

**Required**

* Architectury API 14.0.4 or newer
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.106.1+1.21.2
* NeoForge 21.2.1-beta or newer
* MixinExtras 0.4+ — bundled by both loaders, declared as a required dependency in both mod
  descriptors because `common/…/mixin/common/PlayerMixin` uses `@WrapWithCondition` from it

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing)
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only. There is no
  1.21.2 build, so the integration is compiled against its 1.21.1 API but reports itself absent at
  runtime (`EpicFightManager#isEpicFightUsable` checks the class is loadable) and ParCool falls back
  to its own stamina system.
* BetterThirdPerson — NeoForge only. The newest published build targets 1.21.1; it is compiled against
  and used the same way, and is reported absent on a client that does not have it.
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paragliders) — NeoForge only, compiled
  against Paraglider 21.1.3, the newest published build (its AT name is what the mojmap argument above
  is verified with).

## Status

`./gradlew build` succeeds, `checkCommonLoaderIndependence` passes, and all 28 mixins' targets were
checked mechanically against the 1.21.2 jar. **The game was not launched for this port** — the
in-game checklist (both loaders booting, attribute resolution, key rebinding, one action per family,
two clients on one server, a Prism instance) is listed as untested in [NOTES.md](NOTES.md) §8.

## Attribution and license

ParCool was created by **alRex_U** and is licensed under the
**GNU Lesser General Public License v3.0** — the full text is in [LICENSE](LICENSE).

The Architectury port keeps the same license, mod id, version and project links. Contributions to
the port itself are welcome under the same terms.
