# Building ParCool (Architectury, Minecraft 1.21)

Requires JDK 21. The toolchain is declared through `java { toolchain { languageVersion = 21 } }` in
`build.gradle` and `org.gradle.jvmargs` in `gradle.properties`; there is no `org.gradle.java.home`
and no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.21 |
| Architectury API | 13.0.11 |
| NeoForge | 21.0.167 |
| Fabric Loader | 0.16.5 |
| Fabric API | 0.102.0+1.21 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| ModDevGradle | 1.0.9 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.21` — the target of this port; the Mojang version manifest lists it as a
  `release`.
* `neo_version=21.0.167` — `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`,
  newest non-beta of the `21.0.*` line. NeoForge's version number *is* the Minecraft version it
  targets, so `21.0.x` is 1.21 and `21.1.x` is 1.21.1 (the neighbouring port).
* `fabric_api_version=0.102.0+1.21` — `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`,
  last build with the `+1.21` suffix.
* `loader_version=0.16.5` — the 0.16 line, as in the neighbouring ports; fabric-api 0.102.0 asks
  for `fabricloader >= 0.15.11`, which this satisfies.
* `architectury_api_version=13.0.11` — `https://maven.architectury.dev/dev/architectury/architectury-fabric/maven-metadata.xml`;
  `architectury-fabric 13.0.11` declares `depends.minecraft = "~1.21-"` (>=1.21 <1.22) and
  `architectury-neoforge 13.0.11` requires MC `[1.21,)` / NF `[21.0.110-beta,)`, which 21.0.167
  satisfies. 14.x is the 1.21.2 line.
* `dev.architectury.loom 1.7.435`, `architectury-plugin 3.5.170`, `net.neoforged.moddev 1.0.9` —
  inherited from the 1.21.1 base tree, and all three still resolve for 1.21. Loom prints
  "You are using an outdated version of Architectury Loom!" on every configuration; that is
  expected for this Minecraft and not an error.

The `parchment_minecraft_version`, `parchment_mappings_version` and `yarn_mappings` leftovers the
1.21.1 tree carried are **dropped** here: nothing in the build ever read them, the project is
mojmap-only, and leaving dead version pins behind is what makes the next port guess wrong.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.moddev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, `NeoForgeAttributes`, and the Paraglider / EpicFight / BetterThirdPerson integrations. |

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~15 s
./gradlew build           # from then on
```

The two steps are needed because Architectury Loom resolves the `:common` project dependency while it
*configures* the loader modules, so `:common` has to have been built once. The root `build` task
depends on the `bootstrap` task (an alias of `:common:build`), which covers every case except a truly
fresh checkout.

**If you add a new class to `:common` and the loader module then reports `cannot find symbol` for
it, Loom's cached remap of `:common` is stale.** Clear it once (and do **not** reach for
`./gradlew --stop`, which kills every Gradle daemon on the machine including unrelated projects'):

```bash
rm -rf .gradle/loom-cache/remapped_mods common/build/devlibs common/build/loom-cache fabric/build/loom-cache
```

## Port notes (1.21.1 → 1.21)

1.21.1 is a bugfix release on top of 1.21, and the port confirms it: **no source file under
`common/src`, `fabric/src` or `neoforge/src` had to change** — the port is the toolchain pin plus the
loader metadata ranges. Everything else (payloads, data components, enchantments, attributes,
`ResourceLocation` helpers, the mixin set) is identical between the two versions, and the mixin
verifier below proves the mixin targets still resolve. Where a deltas check did change something:

* `gradle.properties` — `minecraft_version`, `neo_version` (21.0 line), `fabric_api_version`;
  the unused parchment/yarn leftovers dropped (see above).
* `fabric/src/main/resources/fabric.mod.json` — `depends.minecraft` is `~1.21`.
* `neoforge/src/main/resources/META-INF/neoforge.mods.toml` — `[[dependencies.parcool]] minecraft`
  is `[1.21,1.21.2)`, mirroring upstream ParCool's own 1.21 range.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.21-3.4.3.3-fabric.jar
# -> neoforge/build/libs/parcool-neoforge.jar
```

(The NeoForge jar has no version component in its file name because that module never sets a project
version — the classifier alone names it. The published copy is renamed, see below.)

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/neoforge.mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, and the `ServiceLoader` file that binds `ParCoolPlatform`.

Two things about the packaging are load-bearing and are explained at length in the module
`build.gradle` files:

* **the Fabric jar's access widener must be `v2 intermediary`.** `:common:remapJar` leaves it in
  `v2 named` (Loom copies it verbatim because the namespaces match) and Fabric Loader rejects that
  outright on a production client with `ClassTweakerFormatException: Namespace (named) does not match
  current runtime namespace (intermediary)`, aborting the boot before the window exists. The
  authoritative copy therefore comes from `:fabric:remapJar`, and `duplicatesStrategy = EXCLUDE`
  makes the order of the `from { }` clauses decide which one wins.
* **the NeoForge jar is mojmap-named and skips architectury-plugin's `neoForge()` transform**, for
  the reasons in the header of `neoforge/build.gradle`.

The published copies in `/home/sanufsoii/ports/готовые порты/parcool/` are:

```
0.1-mc1.21fabric-3.4.3.3.jar      # = fabric/build/libs/parcool-1.21-3.4.3.3-fabric.jar
0.1-mc1.21neoforge-3.4.3.3.jar    # = neoforge/build/libs/parcool-neoforge.jar
```

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runClient    :neoforge:runServer
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin. A dedicated server refuses to start until it is acknowledged, so before the first
`:fabric:runServer` / `:neoforge:runServer` create `<module>/run/eula.txt` (or
`<module>/run-server/eula.txt` for NeoForge, which uses its own game directory) containing:

```
eula=true
```

Those `run*/` directories are generated by the dev tasks and are not checked in.

`mixin.debug` can be switched for `mixin.debug.export` (writes `<module>/run/.mixin.out/`, one
transformed class per file) when a specific mixin needs inspecting.

### Booting straight into a world

The same quick-play switches the neighbouring ports use are wired into both client runs:

```bash
# client -> dedicated server
./gradlew :neoforge:runServer
./gradlew :fabric:runClient -PparcoolQuickPlay=127.0.0.1:25600

# straight into a saved world
./gradlew :fabric:runClient  -PparcoolQuickPlayWorld="New World"
./gradlew :neoforge:runClient -PparcoolQuickPlayWorld="New World"
```

## Mixin target verification

`tools/verify_mixins.py` re-derives every mixin target against the resolved mojmap jar without
booting the game; with `"defaultRequire": 1` a target that silently stops matching is a hard boot
failure, and this is the cheapest way to catch it:

```bash
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21-loom.mappings.*/*.jar | head -1)
python3 tools/verify_mixins.py "$JAR" common/src/main/java
# checked 228 java files, 52 injection/field targets
# no problems found
```

The checker self-tests first (wrong arity / wrong name must be reported, a real target must stay
silent) and refuses to report anything if that fails — see `tools/README.md`.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*` or `net.neoforged.*`. It is wired into `:common:check`, so `./gradlew build` runs it.

## Verification done for this port

* `./gradlew :common:build` and `./gradlew build` succeed; both distributable jars are produced.
* `tools/verify_mixins.py` passes: `checked 228 java files, 52 injection/field targets`, no problems.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: Fabric jar carries `parcool.accesswidener` in `v2 intermediary`,
  `parcool-common.mixins.json`, `fabric.mod.json`, the ServiceLoader file and both `ParCool` and
  `ParCoolFabric`; the NeoForge jar carries the same plus `META-INF/neoforge.mods.toml`,
  `ParCoolNeoForge` and the common classes.
* Both dedicated dev servers were actually booted against this tree
  (`./gradlew :fabric:runServer` on port 25599 and `./gradlew :neoforge:runserver` on 25600, each
  with `online-mode=false` and its own level): Fabric reports `Done (3.674s)!`, NeoForge reports
  `Done (3.383s)!`, and **both** apply all twelve `common.*` ParCool mixins
  (`ArgumentTypeInfosMixin`, `EntityMixin`, `LevelChunkMixin`, `LivingEntityFallMixin`,
  `LivingEntityMixin`, `ParCoolDataHolderMixin`, `ParCoolDataPersistenceMixin`,
  `PlayerAttributesMixin`, `PlayerForcedPoseMixin`, `PlayerInteractionVisibilityMixin`,
  `PlayerMixin`, `PotionBrewingBuilderMixin`) — the `client.*` half of the mixin set only exists on
  a client, which was not booted for this port. Both servers were stopped after the boot.

Not verified for this port: a client session and an in-world frame (the render path), and the
optional Paraglider/EpicFight/BetterThirdPerson integrations. They compile against the pinned
integration jars, and every entry point guards itself at runtime, but none of them was loaded.
