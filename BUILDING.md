# Building ParCool (Architectury, Minecraft 1.20)

Requires JDK 17. The toolchain is declared through `java { toolchain { languageVersion = 17 } }` in
`build.gradle`; there is no `org.gradle.java.home` and no machine-specific path anywhere in the
build. 1.20 predates the Java 21 requirement that starts with 1.20.5, so 17 is the JDK this tree
builds with (and the `"java": ">=17"` its loader metadata asks for).

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.20 |
| Architectury API | 9.2.14 |
| Forge | 1.20-46.0.14 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.83.0+1.20 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.20` — the target of this port. Mojang's version manifest lists it as a
  `release`; it is the release upstream ParCool supports with its Forge 46.x branch (`1.20.1` is the
  Forge 47.x line; the two share one API generation).
* `forge_version=1.20-46.0.14` — from
  `https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`: the newest build
  for 1.20. The build number carries the Minecraft version (`<mc>-<forge>`), and `46.x` is the 1.20
  line (`47.x` is 1.20.1). There is **no NeoForge for 1.20 or 1.20.1** — NeoForge starts at 1.20.2 —
  so the second loader is plain Forge here. The mod metadata accepts `[46,)`.
* `loader_version=0.16.14` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`: the newest stable
  `0.16.x`, and it clears the `>=0.14.21` floor that fabric-api 0.83.0 declares.
* `fabric_api_version=0.83.0+1.20` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`: the last build
  with the `+1.20` suffix. It declares `minecraft: ">=1.20- <1.20.1-"` and `java: ">=17"`, i.e. it
  is exactly the 1.20 build.
* `architectury_api_version=9.2.14` — from
  `https://maven.architectury.dev/dev/architectury/architectury-forge/maven-metadata.xml`: the newest
  `9.x`. `9.x` is the Architectury line for Minecraft 1.20/1.20.1 — the Fabric half of the same
  version declares `minecraft: "~1.20-"` in its `fabric.mod.json`, so one property covers both
  loaders. `10.x` and later belong to the newer 1.20 releases (the sibling 1.20.4 tree pins
  `11.1.17`).
* `dev.architectury.loom 1.7.435` and `architectury-plugin 3.5.170` — inherited from the 1.21.1 base
  tree; both resolve for 1.20 (the build below proves it). Loom prints "You are using an outdated
  version of Architectury Loom!" on every configuration; that is expected for this Minecraft and not
  an error.
* `forge/gradle.properties` holds exactly one line, `loom.platform=forge`. Architectury Loom selects
  its target platform from a *project* property, so it has to live in `:forge` itself — setting it in
  the root file would switch `:common` and `:fabric` to the Forge platform as well.
* The `parchment_minecraft_version`, `parchment_mappings_version` and `yarn_mappings` leftovers the
  1.21.1 tree carried are **dropped** here, as in the neighbouring ports: nothing in the build ever
  read them and the project is mojmap-only.

| Module   | Toolchain | Contents |
|----------|-----------|----------|
| `common` | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric` | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints and `FabricParCoolPlatform`. |
| `forge`  | `dev.architectury.loom` (Loom's Forge support) | `ParCoolForge` (the `@Mod` entrypoint, plus `ParCoolForgeClient` for the client half), `ForgeParCoolPlatform`, the Paraglider / EpicFight / BetterThirdPerson integrations, and the shared access widener conversion. |

## Building

```bash
./gradlew :common:build   # once on a fresh checkout, ~15 s
./gradlew build           # from then on
```

The two steps are needed because Architectury Loom resolves the `:common` project dependency while it
*configures* the loader modules, so `:common` has to have been built once. The root `build` task
depends on the `bootstrap` task (an alias of `:common:build`), which covers every case except a truly
fresh checkout.

**If you add a new class to `:common` and the loader module then reports `cannot find symbol` for it,
Loom's cached remap of `:common` is stale.** Clear it once (and do **not** reach for
`./gradlew --stop`, which kills every Gradle daemon on the machine including unrelated projects'):

```bash
rm -rf .gradle/loom-cache/remapped_mods common/build/devlibs common/build/loom-cache \
       fabric/build/loom-cache forge/build/loom-cache
```

For a *dev run* the same applies, plus the dev-run refresh order matters: run `./gradlew :common:build`
**first** — Loom recreates the remapped `:common` jar from whatever is on disk while it configures
the loader modules — and only then delete the caches and start the run:

```bash
./gradlew :common:build
rm -rf .gradle/loom-cache/remapped_mods \
       fabric/build/loom-cache forge/build/loom-cache \
       fabric/run/.architectury-transformer forge/run/.architectury-transformer
./gradlew :fabric:runServer        # or :forge:runServer
```

Without the cache refresh a dev run silently keeps loading the previous build of ParCool, and the
boot log ("Registering C2S receiver …") is the cheapest way to confirm which `NetworkRegistries`
copy actually loaded.

## Port notes (1.21.1 → 1.20.1 → 1.20)

This tree started as a copy of the finished 1.20.1 port tree (the first commit is `Import the ParCool
Architectury 1.20.1 port tree as the base for the 1.20 port`) and was then retargeted. 1.20 and
1.20.1 are the **same API generation** — 1.20.1 is the bugfix release of that pair — so:

* `common/src` and `common/src/main/resources` are byte-identical between this tree and the 1.20.1
  port: `git diff` against the import commit touches no file under `common/`. Every source seam the
  1.20.1 port had to make against the 1.21.1 base applies here verbatim, and the sibling 1.20.1
  tree's BUILDING.md lists them (item data components → NBT, `DeltaTracker` → plain float,
  `ItemInteractionResult` → `InteractionResult`, UUID-keyed attribute modifiers,
  `LivingEntity#getVisibilityPercent` instead of the 1.20.2+ `Player#canInteractWithEntity` hook, the
  five-argument `PlayerRenderer#setupRotations`, the `PotionBrewing` static mix list, `BlockEntity`
  save/load without a `HolderLookup.Provider`, the pre-1.21 `data/parcool/recipes/`, `loot_tables/`
  and `tags/blocks/` layout with `pack_format` 15, the GUI/`Codec`/`ResourceLocation` seams and the
  MixinExtras-free Forge runtime). The traps that port hit are unchanged here and documented in the
  same place: the attribute bootstrap order, the client-only registration of the server→client
  receivers, and Forge's single-JPMS-module dev rule (the next section).

* What actually differs from the 1.20.1 tree is only the version retarget and the loader metadata:

* **Versions:** `gradle.properties` — Minecraft 1.20, Forge `1.20-46.0.14`, Fabric API
  `0.83.0+1.20`. Architectury stays `9.2.14` (its Fabric half declares `minecraft: "~1.20-"`, so one
  pin covers both 1.20 and 1.20.1) and the Fabric loader stays `0.16.14`.
* **`mods.toml`:** `loaderVersion = "[46,)"` and the `forge` dependency range `[46,)` — FML's loader
  version equals the Forge minor, 46 on this line, 47 on 1.20.1 — plus the `minecraft` dependency
  range `[1.20,1.21)`.
* **`fabric.mod.json`:** `"minecraft": "~1.20"`.
* **`forge/build.gradle` and `tools/README.md`:** comment wording ("1.20.1-era"/"1.20.x") and the
  verifier's cache glob only — no wiring change.
* **The optional integrations are the 1.20.1-era builds** (Paraglider 20.1.3, EpicFight 20.14.17,
  BetterThirdPerson 1.9.0, ShoulderSurfing 1.20.1-4.11.0): the four mods do not publish a separate
  jar for 1.20, but the classes ParCool compiles against are unchanged between the two releases and
  every entry point guards itself at runtime, so an absent integration degrades to ParCool's own
  behaviour. The Forge metadata accepts `shouldersurfing [1.20,)` for the same reason: the range has
  to admit the only ShoulderSurfing jar that serves 1.20 (`1.20.1-4.11.0`), not the 4.11 API, since
  FML's `ModSorter` treats an installed but out-of-range *optional* dependency as a hard
  early-loading failure. None of the four was loaded here (see the end of this document).

## The one real dev-run trap on Loom's Forge: one JPMS module, not two

`forge/build.gradle` explodes the whole `:common` transform artifact into the module's own
**resources**, and the `common` configuration is on the *compile* classpath only — deliberately not
on `runtimeClasspath`/`developmentForge`. Forge's `BootstrapLauncher` turns every classpath entry into
its own JPMS module and refuses two modules that own the same package, and this mod's own classes
already live in `com.alrex.parcool`, so architectury's usual `developmentForge.extendsFrom common`
aborts the launcher before the game starts:

```
java.lang.module.ResolutionException: Module generated_… contains package
com.alrex.parcool.common.action.instant, module parcool exports package com.alrex.parcool
```

Exploding the artifact into the module's own resources keeps every ParCool class inside the single
`parcool` module, which is also the layout the shipped jar has. Putting the classes in a separate
source-set output directory does *not* work either: Loom hands FML just this module's classes and
resources directories as the `parcool` mod, so classes living elsewhere stay in the unnamed module
and the mod cannot read them (`ClassNotFoundException: com.alrex.parcool.ParCool`).

The same copy is what makes the *production* jar correct: Forge's runtime is SRG-named, and Loom
remaps the jar `:forge:remapJar` processes — so the common code has to be inside that jar (and inside
the AW conversion Loom performs on it) rather than arriving as a second, unremapped artifact.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.20-3.4.3.3-fabric.jar
# -> forge/build/libs/parcool-1.20-3.4.3.3-forge.jar
```

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, and the `ServiceLoader` file that binds `ParCoolPlatform`.

Three things about the packaging are load-bearing and are explained at length in the module
`build.gradle` files:

* **the Forge jar ships SRG bytecode.** `:common`'s transform output is mojmap-named (it resolves
  `net.minecraft.world.entity.player.Player#getAttribute`, which exists in a mojmap dev environment
  and nowhere else), and `:forge:remapJar` rewrites the whole jar named → SRG in one step — classes,
  member references, mixin annotation targets — and converts the access widener into
  `META-INF/accesstransformer.cfg` while it is at it. `:forge:distJar` is that remapped jar plus the
  manifest attributes (`MixinConfigs`), and `assemble` depends on it.
* **the Fabric jar's access widener must be `v2 intermediary`.** `:common:remapJar` leaves it in
  `v2 named` (Loom copies it verbatim because the namespaces match) and Fabric Loader rejects that
  outright on a production client with `ClassTweakerFormatException: Namespace (named) does not match
  current runtime namespace (intermediary)`, aborting the boot before the window exists. The
  authoritative copy therefore comes from `:fabric:remapJar`, and `duplicatesStrategy = EXCLUDE` makes
  the order of the `from { }` clauses decide which one wins.
* **the Fabric jar is built from `:common:remapJar`, not from architectury-plugin's
  `transformProductionFabric`** — same reason: the transform runs in "named" mode and would ship
  mojmap bytecode to an intermediary client.

The published copies in `/home/sanufsoii/ports/готовые порты/parcool/` are:

```
0.1-mc1.20fabric-3.4.3.3.jar     # = fabric/build/libs/parcool-1.20-3.4.3.3-fabric.jar
0.1-mc1.20forge-3.4.3.3.jar      # = forge/build/libs/parcool-1.20-3.4.3.3-forge.jar
```

## Running

```bash
./gradlew :fabric:runClient    :fabric:runServer
./gradlew :forge:runClient     :forge:runServer
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin; that is how the mixin set is verified without a manual client session. A dedicated
server refuses to start until it is acknowledged, so before the first `:fabric:runServer` /
`:forge:runServer` create `fabric/run/eula.txt` / `forge/run/eula.txt` containing:

```
eula=true
```

Those `run/` directories are generated by the dev tasks and are not checked in.

### Booting straight into a world

The same quick-play switches the neighbouring ports use are wired into both client runs:

```bash
# client -> dedicated server
./gradlew :forge:runServer
./gradlew :fabric:runClient -PparcoolQuickPlay=127.0.0.1:25565

# straight into a saved world
./gradlew :fabric:runClient -PparcoolQuickPlayWorld="New World"
./gradlew :forge:runClient  -PparcoolQuickPlayWorld="New World"
```

## Mixin target verification

`tools/verify_mixins.py` re-derives every mixin target against the resolved mojmap jar without
booting the game; with `"defaultRequire": 1` a target that silently stops matching is a hard boot
failure, and this is the cheapest way to catch it:

```bash
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20-*/*.jar | head -1)
python3 tools/verify_mixins.py "$JAR" common/src/main/java
# checked 224 java files, 52 injection/field targets
# no problems found
```

The checker self-tests first (wrong arity / wrong name must be reported, a real target must stay
silent) and refuses to report anything if that fails — see `tools/README.md`.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*`, `net.neoforged.*` or `net.minecraftforge.*`. It is wired into `:common:check`, so
`./gradlew build` runs it.

## Verification done for this port

* `./gradlew :common:build` and `./gradlew build` succeed; both distributable jars are produced.
* `tools/verify_mixins.py` passes: self-test `PASS`, then
  `checked 224 java files, 52 injection/field targets / no problems found` against the resolved
  1.20 mojmap jar.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: the Fabric jar carries `parcool.accesswidener`, `parcool-common.mixins.json`,
  `fabric.mod.json` (`"version": "1.20-3.4.3.3"`), the `ServiceLoader` file, and intermediary names
  in its bytecode (`class_…` / `method_…` in the mixins and the mod classes); the Forge jar carries
  the same assets plus `META-INF/mods.toml` (with `mandatory` and `loaderVersion = "[46,)"`) and
  `META-INF/accesstransformer.cfg`, has no `parcool.accesswidener` left, and its bytecode is
  SRG-remapped (`m_…` member references and SRG mixin annotation strings).
* Both dedicated dev servers were actually booted against this tree (`./gradlew :fabric:runServer`
  with the game directory `fabric/run/` and `./gradlew :forge:runServer` with `forge/run/`, both on
  the default port 25565): Fabric reports `Done (21.066s)!` and Forge reports `Done (26.682s)!`, and
  **both** apply all twelve `common.*` ParCool mixins (`ArgumentTypeInfosMixin`, `EntityMixin`,
  `LevelChunkMixin`, `LivingEntityFallMixin`, `LivingEntityMixin`, `ParCoolDataHolderMixin`,
  `ParCoolDataPersistenceMixin`, `PlayerAttributesMixin`, `PlayerForcedPoseMixin`,
  `PlayerInteractionVisibilityMixin`, `PlayerMixin`, `PotionBrewingMixin`). The Forge log also
  shows the C2S receivers registering (`Registering C2S receiver with id
  parcool:payload.custom_stamina.c2s` …), i.e. the client-only S2C rule does not cost the
  server any of its inbound channels. Both servers were stopped after the boot.

Not verified for this port: a client session on either loader (the mixin verifier covers the eleven
`client.*` targets statically, but no client was booted here), and the optional
Paraglider/EpicFight/BetterThirdPerson/ShoulderSurfing integrations. They compile against the pinned
integration jars and every entry point guards itself at runtime, but none of them was loaded.
