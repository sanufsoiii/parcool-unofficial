# ParCool — Architectury API port

A multiloader port of the Minecraft mod **ParCool** by **alRex_U** (LGPL-3.0), originally built for
NeoForge, reworked onto the [Architectury API](https://github.com/architectury/architectury-api) so
that one codebase ships both a **Fabric** and a **NeoForge** artifact.

Minecraft **1.21.2**, Java **21**, Gradle **8.10.2**.

The port is complete on both loaders: 28 vanilla mixins, 26 actions, the zipline/hook blocks and
the full animation set, and the four optional NeoForge integrations are wired. Both loaders have been
run in a world and in a real Prism instance.

> **1.21.2 is a hybrid version.** It already has the entity render-state rework, `Registry#get`
> returning an `Optional`, `Entity#hurt` returning `void`, the `ClientInput#keyPresses` record, the
> `ClientLevel#addDestroyBlockEffect` hook and `Minecraft#getDeltaTracker` — but it still has
> `ResourceLocation`, `RenderStateShard` composites, a single-mapping-per-key `KeyMapping.MAP` and
> `CompoundTag` entity saves. Every API call in `common` therefore has to be checked against the 1.21.2
> jar rather than assumed from a neighbouring version; the comments in the source say which side of
> each seam this tree sits on.

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
report themselves absent at runtime otherwise.

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
loader-independence check.

## Installing

Drop the matching jar into `mods/`:

* Fabric — `fabric/build/libs/parcool-1.21.2-3.4.3.3-fabric.jar`
* NeoForge — `neoforge/build/libs/parcool-1.21.2-3.4.3.3-neoforge.jar`

## Dependencies

**Required**

* Architectury API 14.0.4 or newer
* Fabric: [Fabric API](https://modrinth.com/mod/fabric-api) 0.106.1+1.21.2
* NeoForge 21.2.1-beta or newer
* MixinExtras 0.4+ — bundled by both loaders, so it is declared as an *optional* dependency in both
  mod descriptors (`custom.mixinextras.required: false` on Fabric, `type = "optional"` on NeoForge);
  `common/…/mixin/common/PlayerMixin` does use `@WrapWithCondition` from it. Declaring it required
  would make NeoForge refuse to start, because the loaders bundle it rather than listing it as a mod.

**Optional**

* [Patchouli](https://modrinth.com/mod/patchouli) — in-game guide
* [ShoulderSurfing](https://www.curseforge.com/minecraft/mc-mods/shouldersurfing)
* [EpicFight](https://www.curseforge.com/minecraft/mc-mods/epicfight) — NeoForge only. There is no
  1.21.2 build, so the integration is compiled against its 1.21.1 API but reports itself absent at
  runtime (`EpicFightManager#isEpicFightUsable` checks the class is loadable) and ParCool falls back
  to its own stamina system.
* BetterThirdPerson — NeoForge only. The newest published build targets 1.21.1; it is compiled against
  and used the same way, and is reported absent on a client that does not have it.
* [Paraglider](https://www.curseforge.com/minecraft/mc-mods/paraglider) — NeoForge only, compiled
  against Paraglider 21.1.3, the newest published build (its AT name is what the mojmap argument above
  is verified with).

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
