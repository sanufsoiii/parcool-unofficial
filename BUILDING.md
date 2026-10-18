# Building ParCool (Architectury, Minecraft 1.20.2)

Requires JDK 17. The toolchain is declared through `java { toolchain { languageVersion = 17 } }` in
`build.gradle`; there is no `org.gradle.java.home` and no machine-specific path anywhere in the
build. 1.20.2 runs on Java 17 (`version.json` inside the merged jar says `java_component:
java-runtime-gamma`, `java_version: 17`), and the loader metadata asks for `"java": ">=17"`.

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.20.2 |
| Architectury API | 10.1.20 |
| Forge | 1.20.2-48.1.0 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.91.6+1.20.2 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.20.2` — the target of this port, the last remaining 1.20.x release of the
  chain.
* `forge_version=1.20.2-48.1.0` — from
  `https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`: the newest 1.20.2
  build (`48.0.1` … `48.0.49`, then `48.1.0`). `48.x` is the 1.20.2 line (`49.x` is 1.20.3/1.20.4,
  `47.x` is 1.20/1.20.1). Unlike the 1.20.3/1.20.4 trees this Forge still launches through the older
  `BootstrapLauncher`, which changes the dev-run story completely — see below.
* `loader_version=0.16.14` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`: the newest stable
  `0.16.x`, and it clears the `>=0.14.22` floor fabric-api 0.91.6+1.20.2 declares.
* `fabric_api_version=0.91.6+1.20.2` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`: the last build
  with the `+1.20.2` suffix. Its own metadata declares `minecraft: ">=1.20.2- <1.20.3-"`,
  `fabricloader: ">=0.14.22"` and `java: ">=17"`.
* `architectury_api_version=10.1.20` — from
  `https://maven.architectury.dev/dev/architectury/architectury-forge/maven-metadata.xml`: the newest
  `10.x`. `10.x` is the Architectury line for Minecraft 1.20.2; architectury-forge 10.1.20's
  `mods.toml` declares `minecraft: "[1.20.2,)"`, `forge: "[48,)"` and `loaderVersion: "[46,)"`.
  (`11.x` is 1.20.3/1.20.4, `9.x` is 1.20/1.20.1.)
* `dev.architectury.loom 1.7.435` and `architectury-plugin 3.5.170` — inherited from the 1.21.1 base
  tree; both resolve for 1.20.2 (the build below proves it). Loom prints "You are using an outdated
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
| `forge`  | `dev.architectury.loom` (Loom's Forge support) | `ParCoolForge` (the `@Mod` entrypoint, plus `ParCoolForgeClient` for the client half), `ForgeParCoolPlatform`, the Paraglider / EpicFight / BetterThirdPerson integrations, the ShoulderSurfing guard, and the shared access widener conversion. |

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

## Port notes (1.21.1 → 1.20.1 → 1.20.4 → 1.20.3 → 1.20.2)

This tree started as a copy of the finished 1.20.3 port tree (the first commit is `Import the ParCool
Architectury 1.20.3 port tree as the base for the 1.20.2 port`) and was then retargeted.

**1.20.2 is *not* the same API generation as 1.20.3/1.20.4**, unlike the 1.20.4 → 1.20.3 step. Two
seams moved and had to be reworked back to their 1.20.1 shape:

* **Block codecs arrive in 1.20.3.** 1.20.3 gave `BlockBehaviour` a `codec()` method and a
  `simpleCodec(...)` factory and made `DirectionalBlock#codec()` abstract, which is why the
  1.20.3/1.20.4 trees give both zipline hook blocks a `CODEC` field and a `codec()` override. On
  1.20.2 `javap` against the merged jar shows no codec member at all on `Block`, `BlockBehaviour` or
  `DirectionalBlock` (`DirectionalBlock`'s only members are `FACING` and the constructor), so those
  overrides do not compile and are simply not there — exactly like the 1.20/1.20.1 trees. Nothing
  else in the mod touches the codec API, so the seam is two deleted overrides.
* **`Checkbox#builder(...)` arrives in 1.20.3.** 1.20.2's `Checkbox` exposes only the public
  constructors `(int x, int y, int width, int height, Component message, boolean selected)` and the
  same with a trailing `showLabel`. So `SettingActionLimitationScreen` and
  `SettingBooleanConfigScreen` construct their checkboxes directly and pass the height as an argument
  (`Checkbox_Item_Height - 1`, same value the 1.20/1.20.1 trees use). `AbstractWidget#setHeight`
  itself *does* exist on 1.20.2 (it is the 1.20.1 tree that lacks it), but the constructor is the
  only place the old widget reads its size.

Everything else the 1.20.3 tree carried was already 1.20.2-compatible, verified against the resolved
1.20.2 mojmap jar rather than assumed:

* `Screen#renderBackground(GuiGraphics, int, int, float)` — present, and the only form `Screen#render`
  calls.
* `GuiEventListener#mouseScrolled(double, double, double, double)` — present (the two-value scroll
  split of the *arguments* still arrives in 1.20.5).
* `CustomRecipe(CraftingBookCategory)` — present, so the recipe class is untouched.
* `PlayerRenderer#setupRotations(AbstractClientPlayer, PoseStack, float, float, float)` — the
  five-argument form; the trailing `scale` argument is a 1.20.5 addition.
* `LivingEntity#getVisibilityPercent(Entity)` — present, so `PlayerInteractionVisibilityMixin` keeps
  its 1.20.1-era target (the newer `Player#canInteractWithEntity` only appears in 1.20.5).
* `PlayerModel#setupAnim(LivingEntity, float × 5)` — present, which is also what the mixin verifier's
  self-test anchors to.
* **`Player#canPlayerFitWithinBlocksAndEntitiesWhen(Pose)` exists in 1.20.2 and is `protected`**
  (`javap` on the merged jar: `protected boolean
  canPlayerFitWithinBlocksAndEntitiesWhen(net.minecraft.world.entity.Pose)`). This is the one place
  where the 1.20.2 tree follows 1.20.3 rather than its 1.20.1 sibling: the access widener entry
  stays and `Crawl` calls the method instead of spelling the check out inline. (1.20.1 does not have
  the method at all, which is why that tree carries a `parcool$canFitStanding` helper.)
* Loader metadata: `loaderVersion = "[48,)"`, `forge [48,)`, `minecraft [1.20.2,1.21)`,
  `architectury [10.0.0,)`, and optional dependencies spelled with Forge's `mandatory = true|false`
  (the `type = "required"|"optional"` spelling only exists from NeoForge 20.5 / Forge 50 on). There
  is deliberately **no `[[mixins]]` section** — Forge 48.1.0's metadata has no such concept either
  (that section arrives with NeoForge 20.5 / Forge 50), and the config is announced through the jar
  manifest's `MixinConfigs` attribute instead.
* `pack.mcmeta` is `pack_format 18`, with no `supported_formats` range: 1.20.2's `version.json` says
  `pack_version: {resource: 18, data: 18}`, i.e. resource and data share one number (the 1.20.3/1.20.4
  trees need `[22, 26]` precisely because those two differ there).
* **The optional integrations keep their established pins** (comment in `forge/build.gradle`):
  Paraglider 20.1.3, Epic Fight 20.14.17, Better Third Person 1.9.0 (1.20–1.20.4) and ShoulderSurfing
  1.20.1-4.11.0. Every entry point checks `isInstalled()` *and* that the API classes resolve, so an
  absent integration degrades to ParCool's own behaviour.

### ShoulderSurfing on 1.20.2: the API no 1.20.2 build has

The integration (`common/…/extern/shouldersurfing/`, plus `shouldersurfing_plugin.json`) is written
against the ShoulderSurfing **4.11.0** API (`IShoulderSurfing#isCameraDecoupled`,
`ICameraCouplingCallback`). No release that names 1.20.2 exposes it: the builds published for
1.20.2–1.20.4 are the earlier **4.2.x** line (CurseForge file 5555248, API 5555249), whose
`IShoulderSurfing` has neither member. Two deliberate consequences:

* it compiles against `1.20.1-4.11.0` (file 6496631) — the only published build with the API — the
  same way the sibling trees do;
* `ShoulderSurfingManager#isCameraDecoupled` catches `NoSuchMethodError | NoClassDefFoundError` and
  returns `false`. That is exactly the answer an absent ShoulderSurfing gives, so a 4.2.x install
  simply does not enter ParCool's camera-coupling path (its dodge falls back to the dodge's own
  direction logic) instead of throwing on the first dodge. ShoulderSurfing's own plugin loader
  catches `Throwable`, so the coupling plugin fails soft on its side too.

`mods.toml` therefore asks for `shouldersurfing [1.20.2,)`: a Maven range matches the *installed mod
version*, not the API version, and FML 48's `ModSorter` treats an installed-but-out-of-range optional
dependency as a hard `EarlyLoadingException` (the "Unsupported installed optional dependencies"
error) — an integration that is *present* must not be rejected by a version range no jar for that
Minecraft version can satisfy. (The 1.20.1, 1.20, 1.20.3 and 1.20.4 trees carry `[1.20.1,)` /
`[1.20,)` / `[1.20.3,)` / `[1.20.4,)` for the same reason.)

## The single JPMS module rule (same as the neighbouring Forge trees)

`forge/build.gradle` explodes the whole `:common` transform artifact into the module's own
**resources**, and the `common` configuration is on the *compile* classpath only — deliberately not
on `runtimeClasspath`/`developmentForge`. Forge's launcher turns every classpath entry into its own
JPMS module and refuses two modules that own the same package, and this mod's own classes already
live in `com.alrex.parcool`, so architectury's usual `developmentForge.extendsFrom common` aborts the
launcher before the game starts:

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

## Why this tree needs none of the 1.20.3/1.20.4 dev-run workarounds

The 1.20.3 tree documents two Forge-49-specific dev-launch traps — the `MOD_CLASSES` union fixer
that 49.0.2's bootstrap 2.0.0 never runs, and the classpath-derived module layer that chokes on the
LWJGL natives jars Loom keeps. **Neither exists on this line**, so `forge/build.gradle` carries
neither the `forgeDependencies` bootstrap override nor the `runServer` LWJGL filter:

* Forge 1.20.2-48.1.0's userdev config pins `net.minecraftforge:bootstrap:**1.2.0**` and launches
  through `net.minecraftforge.bootstrap.BootstrapLauncher`, not through 49.x's
  `net.minecraftforge.bootstrap.Bootstrap`. The launcher takes its module layer from the run config's
  explicit `-p {modules}` list (`bootstrap 1.2.0`, `securemodules 2.2.3`, `unsafe 0.9.2`,
  `asm/asm-tree/asm-util/asm-commons/asm-analysis 9.6`, `JarJarFileSystems 0.3.19`,
  `guava 32.1.2-jre`, `failureaccess 1.0.1`) plus `--add-modules ALL-MODULE-PATH`, and passes the
  classpath through the `legacyClassPath.file` / `ignoreList` / `mergeModules` properties. Nothing
  derives modules from the classpath, so the client-only LWJGL jars Loom strips on a server classpath
  (and the natives jars it keeps) never become modules of the launch layer — architectury-loom#191
  simply does not apply here.
* `MOD_CLASSES` is consumed by **FML 48 itself**. The dev-run debug log shows both halves:

  ```
  [main/DEBUG] (CommonLaunchHandler) Got mod coordinates main%%…/forge/build/resources/main:main%%…/forge/build/classes/java/main:parcool%%… from env
  [main/DEBUG] (AbstractModProvider) Found META-INF/mods.toml mod of type null: […/forge/build/resources/main, …/forge/build/classes/java/main]
  ```

  i.e. the two directories are already the one dev mod file — there is no union fixer to wake up, no
  bootstrap-2.1.8 override to declare, and no `…/resources/main constructed 0 mods: [], but had 1
  mods specified: [parcool]` failure mode. (For completeness: the `MOD_CLASSES` string Loom emits
  here is identical in shape to the 1.20.3 one, `main%%…` + `parcool%%…`, and FML 48 splits it on
  `File.pathSeparator` — the separator is only a problem for the 49-line fixer.)

## Forge 49's LWJGL/module interplay, for reference

The 1.20.4 tree documents the failure text in full (`FindException: Module org.lwjgl.openal not found,
required by org.lwjgl.openal.natives` from `net.minecraftforge.bootstrap.Bootstrap.moduleMain`). It is
recorded here only so that nobody "restores" the workaround: this tree's launcher is the pre-49 one,
the run boots without any classpath filtering, and adding the filter would be cargo cult.

## Loom cache traps seen while porting

* **A remapped architectury jar carrying stub classes.** If `:fabric:runServer` dies inside
  `dev.architectury.registry.registries.RegistrarManager._get` (or
  `dev.architectury.platform.Platform.getEnvironment`) with `java.lang.AssertionError` while the
  stack trace points at `architectury-fabric-<v>.jar`, that file under
  `.gradle/loom-cache/remapped_mods/…/dev/architectury/architectury-fabric/<v>/` is a half-written
  cache entry holding architectury's *common-jar stub* classes instead of the platform's real ones.
  Delete the whole `remapped_mods` directory and re-run; Loom regenerates it. The quickest way to
  tell the two apart is `javap -c` on `RegistrarManager._get`: the platform jar must show
  `invokestatic …RegistrarManagerImpl._get`.
* **A missing `mappings-srg.tiny`.** If `:common:remapJar` fails with `Failed to read mappings from:
  …/fabric-loom/1.20.2/loom.mappings.1_20_2.layered+hash.40545-v2/mappings-srg.tiny`, the plain
  (Fabric-side) mappings directory is incomplete while its Forge sibling
  `…-v2-forge-1.20.2-48.1.0/` is not. Copy `mappings-srg.tiny` over from the sibling directory; the
  file is byte-identical between working trees (md5-checked on the 1.20.1 line).
* **`$` in `META-INF/mods.toml`.** `:forge:processResources` runs `expand` over that one file, so a
  comment containing something like `ModInfo$ModVersion` aborts the build with `Missing property
  (ModVersion) for Groovy template expansion`. Spell such names out in prose (the file says "a field
  of the parsed ModVersion record").

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.20.2-3.4.3.3-fabric.jar
# -> forge/build/libs/parcool-1.20.2-3.4.3.3-forge.jar
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

The two jars are distributed under these names:

```
0.1-mc1.20.2fabric-3.4.3.3.jar     # = fabric/build/libs/parcool-1.20.2-3.4.3.3-fabric.jar
0.1-mc1.20.2forge-3.4.3.3.jar      # = forge/build/libs/parcool-1.20.2-3.4.3.3-forge.jar
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
booting the game; with `injectors.defaultRequire = 1` a target that silently stops matching is a hard
boot failure, and this is the cheapest way to catch it:

```bash
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.2-loom.mappings.*/*.jar | head -1)
python3 tools/verify_mixins.py "$JAR" common/src/main/java
# checked 224 java files, 52 injection/field targets
# no problems found
```

The checker self-tests first (wrong arity / wrong name must be reported, a real target must stay
silent) and refuses to report anything if that fails — see `tools/README.md`, which also spells out
the 1.20.2 anchor (`PlayerModel#setupAnim(float × 5)`) used by that self-test.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*`, `net.neoforged.*` or `net.minecraftforge.*`. It is wired into `:common:check`, so
`./gradlew build` runs it.

## Verification done for this port

* `./gradlew :common:build` and `./gradlew build` succeed; both distributable jars are produced
  (Fabric 1 188 123 bytes / sha256 `e0222e83…`, Forge 1 212 900 bytes / sha256 `22c25ac2…`).
* `tools/verify_mixins.py` passes: self-test `PASS`, then
  `checked 224 java files, 52 injection/field targets / no problems found` against the resolved
  1.20.2 mojmap merged jar.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: the Fabric jar carries `parcool.accesswidener` (`v2 intermediary`),
  `parcool-common.mixins.json` (12 `common.*` + 11 `client.*` entries, `compatibilityLevel JAVA_17`,
  `injectors.defaultRequire 1`), `fabric.mod.json` (loader `>=0.14.22`, minecraft `~1.20.2`,
  architectury `>=10.0.0`), the `ServiceLoader` file, and intermediary names in its bytecode
  (5366 `class_…` / `method_…` / `field_…` references, 0 SRG ones); the Forge jar carries the same
  assets plus `META-INF/mods.toml` (with `mandatory`, `loaderVersion = "[48,)"`,
  `minecraft [1.20.2,1.21)`), `META-INF/accesstransformer.cfg` with SRG member names
  (`m_295652_`, `f_20931_`, …), the `MixinConfigs: parcool-common.mixins.json` manifest attribute,
  has no `parcool.accesswidener` left, and its bytecode is SRG-remapped (1487 `m_…` / `f_…` member
  references, 0 intermediary ones).
* Both dedicated dev servers were actually booted against this tree (`./gradlew :fabric:runServer`
  with the game directory `fabric/run/` and `./gradlew :forge:runServer` with `forge/run/`, both on
  the default port 25565): Fabric reports `Done (10.808s)!` and Forge reports `Done (10.485s)!`, and
  **both** apply all twelve `common.*` ParCool mixins (`ArgumentTypeInfosMixin`, `EntityMixin`,
  `LevelChunkMixin`, `LivingEntityFallMixin`, `LivingEntityMixin`, `ParCoolDataHolderMixin`,
  `ParCoolDataPersistenceMixin`, `PlayerAttributesMixin`, `PlayerForcedPoseMixin`,
  `PlayerInteractionVisibilityMixin`, `PlayerMixin`, `PotionBrewingMixin`). The Forge log also shows
  the C2S receivers registering (`Registering C2S receiver with id
  parcool:payload.custom_stamina.c2s` …), and its debug log the dev-mod discovery described above.
  Both servers were stopped after the boot. (The dev-run JVMs are the Gradle daemon's, here Java 21;
  the Java 17 toolchain is what compiles the classes.)
* The access-widener entries were re-verified against the resolved 1.20.2 jar with `javap`:
  `LivingEntity#swimAmount`/`swimAmountO` and `Entity#onGround` are private, `ArgumentTypeInfos#register`
  and `LevelResource(String)` are private, and `Player#canPlayerFitWithinBlocksAndEntitiesWhen` is
  `protected`.

Not verified for this port: a client session on either loader (the mixin verifier covers the eleven
`client.*` targets statically, but no client was booted here), and the optional
Paraglider/EpicFight/BetterThirdPerson/ShoulderSurfing integrations. They compile against the pinned
integration jars and every entry point guards itself at runtime, but none of them was loaded.
