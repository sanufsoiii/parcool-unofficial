# Building ParCool (Architectury, Minecraft 1.20.5)

Requires JDK 21. The toolchain is declared through `java { toolchain { languageVersion = 21 } }` in
`build.gradle` and `org.gradle.jvmargs` in `gradle.properties`; there is no `org.gradle.java.home`
and no machine-specific path anywhere in the build. (1.20.5 is the first Minecraft release that
requires Java 21 — unlike 1.20.4 below it, there is no 17 fallback here.)

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.20.5 |
| Architectury API | 12.0.29 |
| NeoForge | 20.5.21-beta |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.97.8+1.20.5 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| NeoGradle (userdev) | 7.0.171 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.20.5` — the target of this port; Mojang's version manifest lists it as a
  `release`, and it is the release right before 1.20.6.
* `neo_version=20.5.21-beta` — `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`,
  the newest of the `20.5.*` line (the whole line is marked `-beta`, that is how it was published).
  NeoForge's version number *is* the Minecraft version it targets, so `20.5.x` is 1.20.5, `20.6.x` is
  1.20.6, `21.0.x` is 1.21 and `21.1.x` is 1.21.1 (the base tree).
* `fabric_api_version=0.97.8+1.20.5` — `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`,
  the last build with the `+1.20.5` suffix (`0.100.x` already moved to 1.20.6). Its own
  `fabric.mod.json` asks for `fabricloader >=0.15.6`, Java `>=21` and `minecraft >=1.20.5- <1.20.6-`.
* `loader_version=0.16.14` — `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`,
  the newest stable `0.16.x`; it satisfies the `>=0.15.6` that fabric-api declares.
* `architectury_api_version=12.0.29` — `https://maven.architectury.dev/dev/architectury/architectury-neoforge/maven-metadata.xml`,
  the last `12.0.x`. `architectury-neoforge 12.0.29` declares `minecraft [1.20.5,)` and
  `neoforge [20.5.0-beta,)`, which 20.5.21-beta satisfies. `12.1.x` is the 1.20.6 line and `13.x`
  is the 1.21 line.
* `dev.architectury.loom 1.7.435` and `architectury-plugin 3.5.170` — inherited from the 1.21.1 base
  tree; both resolve for 1.20.5 (the build below proves it). Loom prints
  "You are using an outdated version of Architectury Loom!" on every configuration; that is expected
  for this Minecraft and not an error.
* `net.neoforged.gradle.userdev 7.0.171` — **NeoGradle, not ModDevGradle.** ModDevGradle resolves
  NeoForge through the `neoforge-moddev-bundle` capability, and the 20.5 line publishes no such
  variant: with MDG 1.0.0 and 0.1.131 the configuration fails with "Unable to find a variant
  providing the requested capability 'net.neoforged:neoforge-moddev-bundle'" (the capability starts
  at 20.6). NeoGradle consumes the `userdev` artifact that the 20.5 line does publish and is what the
  official `MDK-1.20.5-NeoGradle` uses. Within the `7.x` line, `7.1.39` requires Gradle 9 APIs and
  `7.0.192` fails under Gradle 8.10.2, while `7.0.171` configures, compiles and boots this project.
* The `parchment_minecraft_version`, `parchment_mappings_version` and `yarn_mappings` leftovers the
  1.21.1 tree carried are **dropped** here, as in the neighbouring ports: nothing in the build ever
  read them and the project is mojmap-only.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.gradle.userdev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, `NeoForgeAttributes`, and the Paraglider / EpicFight / BetterThirdPerson integrations. |

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

## Port notes (1.21.1 → 1.20.6 → 1.20.5)

This tree started as a copy of the finished 1.20.6 port (the first commit is
`Import the 1.20.6 port tree as the base for the 1.20.5 port`) and was then retargeted. 1.20.5 and
1.20.6 are the **same API generation**, so:

* `common/src` and `common/src/main/resources` are byte-identical between this tree and the 1.20.6
  port — `git diff` against the import commit touches no file under `common/`. Every source seam the
  1.20.6 port had to make against the 1.21.1 base applies here verbatim; that tree's BUILDING.md
  lists them (public `ResourceLocation` constructors, `(UUID, String)`-keyed attribute modifiers,
  plain `float` partial ticks instead of `DeltaTracker`, the old `VertexConsumer` builder names,
  `DyeColor#getTextureDiffuseColors()`, `CraftingContainer`, `new ItemStack(supplier)`, and the
  pre-1.21 `data/parcool/recipes/`, `loot_tables/` and `tags/blocks/` directories).
* What actually differs from the 1.20.6 tree is only the version retarget and the loader modules:

* **Versions:** `gradle.properties` (Minecraft 1.20.5, NeoForge 20.5.21-beta, Fabric API
  0.97.8+1.20.5, Architectury 12.0.29) and the `settings.gradle` plugin id.
* **Toolchain:** `:neoforge` moved from `net.neoforged.moddev` to `net.neoforged.gradle.userdev`
  (NeoGradle), see the version table above for why.
* **`@Mod` has no `dist` attribute yet on this line.** The 1.20.6 port registers
  `ParCoolNeoForgeClient` as a second `@Mod(value = ParCool.MOD_ID, dist = Dist.CLIENT)` class;
  20.5's `@Mod` accepts no `dist`, so the client-only hook is an
  `@EventBusSubscriber(modid = ParCool.MOD_ID, bus = MOD, value = Dist.CLIENT)` subscribing to
  `FMLClientSetupEvent` instead. FML only registers `Dist.CLIENT` subscribers on the physical
  client, which is exactly the guarantee the `dist` attribute gives on 1.20.6.
* **`loaderVersion` stays `[3,)`.** The 20.5 line implements `javafml` 3 (`fml_loader@3.0.18` in the
  launch log), like 20.6; the 21.0 line implements 4, where the base tree's `[4,)` comes from.
* **Access transformer** (`neoforge/src/main/resources/META-INF/accesstransformer.cfg`): NeoForge
  dropped SRG runtime names in this very line, so the file is written in Mojang names
  (`RenderStateShard NO_TEXTURE`, `Entity onGround`, `ArgumentTypeInfos register`,
  `Player canPlayerFitWithinBlocksAndEntitiesWhen`, `LevelResource <init>`,
  `LivingEntity swimAmount[O]`) — the same members the Fabric access widener widens. Under
  NeoGradle the file is also registered through `minecraft { accessTransformers.file ... }`, or the
  dev runs would hit `IllegalAccessError` on every widened member.
* **Loader metadata ranges:** `fabric.mod.json` — `minecraft ~1.20.5`, `architectury >=12.0.0`;
  `neoforge.mods.toml` — `neoforge [20.5,)`, `minecraft [1.20.5,1.21)`, `architectury [12.0.0,)`,
  `shouldersurfing [1.20.5,)`.
* **`pack.mcmeta`** is `"pack_format": 32, "supported_formats": [32, 41]` — the same resource 32 /
  data 41 numbers the whole 1.20.5/1.20.6 line uses.
* **Optional integrations:** none of Paraglider, EpicFight, ShoulderSurfing or BetterThirdPerson
  published a 1.20.5-era build — their lines jump from the 1.20.x era to 1.21 — so the pins stay on
  the 1.21.1-era builds and the entry points guard themselves at runtime (see the comment block in
  `neoforge/build.gradle`).
* The mixin set is unchanged: all 12 `common.*` and 12 `client.*` targets resolve against the 1.20.5
  jar (proven below by the verifier). `parcool-common.mixins.json` keeps
  `"compatibilityLevel": "JAVA_21"`; Fabric Loader logs that this exceeds the maximum level
  sponge-mixin 0.8.7 supports (`JAVA_13`) and selects the config anyway — the mixins apply, as the
  server boots below show.

### The one real dev-run trap on NeoGradle

`neoforge/build.gradle` explodes the whole `:common` transform artifact into the module's own
**resources** (`processResources { from(commonArtifactFile.map { zipTree(it) }) }`). That is not
cosmetic: NeoGradle hands FML a dev mod built from the module's classes dirs and resources dir only —
extra source-set output dirs and plain classpath jars are *not* part of the mod's file set. With the
`:common` artifact merely on the classpath, the game starts with an empty `parcool` mod and Mixin
aborts during `PREPARE` with

```
InvalidMixinException: The specified mixin 'com.alrex.parcool.mixin.common.ArgumentTypeInfosMixin' was not found
```

Putting the common classes, assets and data inside the mod's own resources makes the dev run see
exactly what the distributable jar contains — and as a bonus this module's own `jar` task becomes
self-contained, so `distJar` is just `sourceSets.main.output` plus the `ServiceLoader` binding line
copied from the integration jars. (The 1.20.6 port did not need this because ModDevGradle *does*
include extra source-set output dirs in the dev mod.)

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.20.5-3.4.3.3-fabric.jar
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
  the reasons in the header of `neoforge/build.gradle` (Loom 1.7.x cannot merge the Mojang and
  NeoForge mappings on this Minecraft).

The two jars are distributed under these names:

```
0.1-mc1.20.5fabric-3.4.3.3.jar      # = fabric/build/libs/parcool-1.20.5-3.4.3.3-fabric.jar
0.1-mc1.20.5neoforge-3.4.3.3.jar    # = neoforge/build/libs/parcool-neoforge.jar
```

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runClient    :neoforge:runServer
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin. A dedicated server refuses to start until the EULA is acknowledged, so before the
first `:fabric:runServer` / `:neoforge:runServer` create `fabric/run/eula.txt` (or
`neoforge/run-server/eula.txt` — the NeoForge runs use their own game directory, `neoforge/run` for
the client) containing:

```
eula=true
```

Those `run*/` directories are generated by the dev tasks and are not checked in.

### Booting straight into a world

The same quick-play switches the neighbouring ports use are wired into both client runs:

```bash
# client -> dedicated server
./gradlew :neoforge:runServer
./gradlew :fabric:runClient -PparcoolQuickPlay=127.0.0.1:25565

# straight into a saved world
./gradlew :fabric:runClient  -PparcoolQuickPlayWorld="New World"
./gradlew :neoforge:runClient -PparcoolQuickPlayWorld="New World"
```

## Mixin target verification

`tools/verify_mixins.py` re-derives every mixin target against the resolved mojmap jar without
booting the game; with `"defaultRequire": 1` a target that silently stops matching is a hard boot
failure, and this is the cheapest way to catch it:

```bash
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.5-*/*.jar | head -1)
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
* `tools/verify_mixins.py` passes: self-test `PASS`, then
  `checked 228 java files, 52 injection/field targets / no problems found` against the resolved
  1.20.5 mojmap jar.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: the Fabric jar carries `parcool.accesswidener` in `v2 intermediary`,
  `parcool-common.mixins.json`, `fabric.mod.json`, the ServiceLoader file and `ParCool` +
  `ParCoolFabric` + `FabricParCoolPlatform`; the NeoForge jar carries the same plus
  `META-INF/neoforge.mods.toml`, `META-INF/accesstransformer.cfg`, `ParCoolNeoForge` and
  `NeoForgeParCoolPlatform`.
* Both dedicated dev servers were actually booted against this tree (`./gradlew :fabric:runServer`
  with the game directory `fabric/run/` and `./gradlew :neoforge:runServer` with
  `neoforge/run-server/`, both on the default port 25565, one after the other): Fabric reports
  `Done (10.157s)!` and NeoForge reports `Done (3.521s)!`, and **both** apply all twelve `common.*`
  ParCool mixins (`ArgumentTypeInfosMixin`, `EntityMixin`, `LevelChunkMixin`,
  `LivingEntityFallMixin`, `LivingEntityMixin`, `ParCoolDataHolderMixin`,
  `ParCoolDataPersistenceMixin`, `PlayerAttributesMixin`, `PlayerForcedPoseMixin`,
  `PlayerInteractionVisibilityMixin`, `PlayerMixin`, `PotionBrewingBuilderMixin`). Both servers were
  stopped after the boot.

Not verified for this port: a client session on either loader for 1.20.5 (the mixin verifier covers
the twelve `client.*` targets statically, but no client was booted here), and the optional
Paraglider/EpicFight/BetterThirdPerson integrations. They compile against the pinned integration
jars and every entry point guards itself at runtime, but none of them was loaded.
