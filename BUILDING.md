# Building ParCool (Architectury, Minecraft 1.20.4)

Requires JDK 17. The toolchain is declared through `java { toolchain { languageVersion = 17 } }` in
`build.gradle`; there is no `org.gradle.java.home` and no machine-specific path anywhere in the
build. 1.20.4 predates the Java 21 requirement that starts with 1.20.5, so 17 is the JDK this tree
builds with (and the `"java": ">=17"` its loader metadata asks for).

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.20.4 |
| Architectury API | 11.1.17 |
| Forge | 1.20.4-49.2.13 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.97.3+1.20.4 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.20.4` — the target of this port, and the release upstream ParCool's 1.20.4
  branch serves. 1.20.2/1.20.3/1.20.4 are one API generation, and **this tree is the anchor for the
  1.20.3 and 1.20.2 ports**, the way the 1.20.1 tree is the anchor for 1.20.
* `forge_version=1.20.4-49.2.13` — from
  `https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`: the newest build
  for 1.20.4. `49.x` is the 1.20.3/1.20.4 line (`48.x` is 1.20.2, `47.x` is 1.20/1.20.1), and the
  mod metadata accepts `[49,)`. Upstream's 1.20.4 branch pins an older `1.20.4-49.1.0`.
* `loader_version=0.16.14` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`: the newest stable
  `0.16.x`, and it clears the `>=0.15.1` floor that fabric-api 0.97.3 declares.
* `fabric_api_version=0.97.3+1.20.4` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`: the last build
  with the `+1.20.4` suffix. It declares `minecraft: ">=1.20.3- <1.20.5-"`, `fabricloader:
  ">=0.15.1"` and `java: ">=17"`, i.e. it covers exactly this release pair (and 1.20.3).
* `architectury_api_version=11.1.17` — from
  `https://maven.architectury.dev/dev/architectury/architectury-forge/maven-metadata.xml`: the newest
  `11.x`. `11.x` is the Architectury line for Minecraft 1.20.3/1.20.4 — its Forge half declares
  `minecraft: "[1.20.3,)"` and `forge: "[49,)"`, its Fabric half `minecraft: "~1.20.3-"` — so one
  property covers both loaders of the pair. (`10.x` belongs to 1.20.2, `9.x` to 1.20/1.20.1.)
* `dev.architectury.loom 1.7.435` and `architectury-plugin 3.5.170` — inherited from the 1.21.1 base
  tree; both resolve for 1.20.4 (the build below proves it). Loom prints "You are using an outdated
  version of Architectury Loom!" on every configuration; that is expected for this Minecraft and not
  an error. The bug in the next-but-one section comes from this Loom, not from the project.
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

## Port notes (1.21.1 → 1.20.1 → 1.20.4)

This tree started as a copy of the finished 1.20.1 port tree (the first commit is `Import the ParCool
Architectury 1.20.1 port tree as the base for the 1.20.4 port`) and was then retargeted. The
1.21.1 → 1.20.1 seam work — item data components → NBT, `DeltaTracker` → plain float,
`ItemInteractionResult` → `InteractionResult`, UUID-keyed attribute modifiers, `BlockEntity`
save/load without a `HolderLookup.Provider`, the pre-1.21 `data/parcool/recipes/`, `loot_tables/` and
`tags/blocks/` layout, the `Codec`/`ResourceLocation` seams, the MixinExtras-free Forge runtime — is
unchanged here and documented in the **1.20.1 tree's** BUILDING.md, together with the traps that port
hit (the attribute bootstrap order, the client-only registration of the server→client receivers and
Forge's single-JPMS-module dev rule).

**1.20.4 sits one API generation later** (1.20.2/1.20.3/1.20.4 are one generation), and this is what
had to move relative to the 1.20.1 tree:

* **GUI:** `renderBackground` is the four-argument form (`GuiGraphics, int, int, float`), the only
  one `Screen#render` calls since 1.20.2 — the 1.20.1 tree's one-argument override has nothing to
  override any more. `GuiEventListener#mouseScrolled` is the four-double form (the screens keep their
  three-double helper for their own call sites). The config screens build `Checkbox` through
  `Checkbox.builder(label, font).selected(...).pos(...).build()` and size it with
  `AbstractWidget#setHeight` — 1.20.1 has neither the builder nor `setHeight`, only a constructor
  that takes the height.
* **`Crawl`** calls `Player#canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING)` again, which is
  `protected` in 1.20.4, so `parcool.accesswidener` re-adds it to the accessible set. The inline
  `noCollision` check the 1.20.1 tree spells out exists there only because 1.20.1 has no such method
  at all. (`Entity#canEnterPose`, the 1.21.1 target, still does not exist.)
* **Zipline hook blocks re-add `codec()`.** `DirectionalBlock` declares `codec()` abstract in 1.20.4
  (it does not in 1.20.1), and `simpleCodec` is inherited from `BlockBehaviour`, so
  `IronZiplineHookBlock` and `WoodenZiplineHookBlock` each carry a two-line `MapCodec` override. The
  1.20.1 tree *removed* those methods and its BUILDING.md never recorded it; for the trees after this
  one the difference is intentional, not a regression.
* **Recipes:** `CustomRecipe` carries only the crafting-book category again
  (`CustomRecipe(CraftingBookCategory)`); the id-carrying constructor is a 1.20.1-only shape that
  comes back with the `RecipeHolder` rework in 1.20.5. `FastColor.ARGB32` still has only the
  four-argument `color(a, r, g, b)`.
* **Seams that do *not* move at this step:** `PlayerRenderer#setupRotations` is still the
  five-argument form (`…PoseStack;FFF)V`; the trailing `scale` argument arrives with 1.20.5. The
  visibility hook is still `LivingEntity#getVisibilityPercent` (the `Player#canInteractWithEntity`
  target of the newer ports is 1.20.5+).
* **Loader metadata:** `fabric.mod.json` asks for `minecraft: "~1.20.4"`, `fabricloader: ">=0.15.1"`
  (the floor fabric-api 0.97.3 declares) and `architectury: ">=11.0.0"`. `mods.toml` carries
  `loaderVersion = "[49,)"`, `minecraft [1.20.4,1.21)`, `architectury [11.0.0,)`. `pack.mcmeta` is
  `pack_format 22` with `supported_formats [22, 26]` (1.20.4 uses resource format 22 and data format
  26). The mods.toml still spells optional dependencies with Forge's `mandatory = true|false`; the
  newer `type = "required"|"optional"` spelling only exists from NeoForge 20.5 / Forge 50 on, which
  is what the 1.20.5/1.20.6 sibling trees carry. There is deliberately **no `[[mixins]]` section** —
  Forge 49.2.13's metadata has no such concept, and the config is announced through the jar manifest's
  `MixinConfigs` attribute instead.
* **The optional integrations are pinned to the closest builds that exist** (comment in
  `forge/build.gradle`): Paraglider 20.1.3, Epic Fight 20.14.17, Better Third Person 1.9.0 (a
  1.20–1.20.4 jar) and ShoulderSurfing 1.20.1-4.11.0. Every entry point checks `isInstalled()` *and*
  that the API classes resolve, so an absent integration degrades to ParCool's own behaviour.

### ShoulderSurfing on 1.20.4: the API no 1.20.4 build has

The integration (`common/…/extern/shouldersurfing/`, plus `shouldersurfing_plugin.json`) is written
against the ShoulderSurfing **4.11.0** API (`IShoulderSurfing#isCameraDecoupled`,
`ICameraCouplingCallback`). No release that names 1.20.4 exposes it: the 1.20.2–1.20.4 releases are
the earlier **4.2.x** line (CurseForge file 5555248, API 5555249), whose `IShoulderSurfing` has
neither member. Two deliberate consequences:

* it compiles against `1.20.1-4.11.0` (file 6496631) — the only published build with the API — the
  same way the sibling 1.20 tree does;
* `ShoulderSurfingManager#isCameraDecoupled` catches `NoSuchMethodError | NoClassDefFoundError` and
  returns `false`. That is exactly the answer an absent ShoulderSurfing gives, so a 4.2.x install
  simply does not enter ParCool's camera-coupling path (its dodge falls back to the dodge's own
  direction logic) instead of throwing on the first dodge. ShoulderSurfing's own plugin loader
  catches `Throwable`, so the coupling plugin fails soft on its side too.

`mods.toml` therefore asks for `shouldersurfing [1.20.4,)`: a Maven range matches the *installed
mod version*, not the API version, and FML 49's `ModSorter` treats an installed-but-out-of-range
optional dependency as a hard `EarlyLoadingException` — an integration that is *present* must not be
rejected by a version range that no jar for that Minecraft version can satisfy. (The 1.20.1 and 1.20
trees carry `[1.20.1,)` / `[1.20,)` for the same reason; `[4.11.0,)` there would exclude every
ShoulderSurfing jar published for those releases, including the one the sibling trees compile
against.)

## The one real dev-run trap on Loom's Forge: one JPMS module, not two

`forge/build.gradle` explodes the whole `:common` transform artifact into the module's own
**resources**, and the `common` configuration is on the *compile* classpath only — deliberately not
on `runtimeClasspath`/`developmentForge`. Forge's bootstrap turns every classpath entry into its own
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

## Forge 49's module bootstrap and the LWJGL jars Loom splits off

Forge 49 replaced the 1.20.1-era `BootstrapLauncher` + `legacyClassPath.file` launch with
`net.minecraftforge.bootstrap.Bootstrap`, and that bootstrap derives its JPMS module layer straight
from the runtime classpath: every classpath jar that declares a module — including the Java 9
multi-release `module-info.class` inside the LWJGL **natives** jars — becomes a module of that layer.
Architectury Loom 1.7 strips the client-only LWJGL *main* jars from a dev **server** classpath but
keeps those natives jars (architectury-loom#191, open since January 2024 and still open), so the
launch aborts before Minecraft starts:

```
[Architectury Transformer] Read classpath in 1.8 s
Caused by: java.lang.module.FindException: Module org.lwjgl.openal not found, required by org.lwjgl.openal.natives
	at net.minecraftforge.bootstrap.Bootstrap.moduleMain(Bootstrap.java:166)
```

`forge/build.gradle` therefore filters the whole `org.lwjgl` group out of the `runServer` classpath in
an `afterEvaluate` block — a dedicated server never loads LWJGL, so nothing is lost — and the launch
proceeds. `runClient` is left alone: issue #191 also reports the client side breaking on some
Forge 50/1.20.6 combinations, but no client was booted here (see the end of this document).

## Loom cache traps seen on this machine

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
  …/fabric-loom/1.20.4/loom.mappings.1_20_4.layered+hash.40545-v2/mappings-srg.tiny`, the plain
  (Fabric-side) mappings directory is incomplete while its Forge sibling
  `…-v2-forge-1.20.4-49.2.13/` is not. Copy `mappings-srg.tiny` over from the sibling directory; the
  file is byte-identical between working trees (md5-checked on the 1.20.1 line).
* **`$` in `META-INF/mods.toml`.** `:forge:processResources` runs `expand` over that one file, so a
  comment containing something like `ModInfo$ModVersion` aborts the build with `Missing property
  (ModVersion) for Groovy template expansion`. Spell such names out in prose (the file says "the
  parsed ModVersion record").

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.20.4-3.4.3.3-fabric.jar
# -> forge/build/libs/parcool-1.20.4-3.4.3.3-forge.jar
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
0.1-mc1.20.4fabric-3.4.3.3.jar     # = fabric/build/libs/parcool-1.20.4-3.4.3.3-fabric.jar
0.1-mc1.20.4forge-3.4.3.3.jar      # = forge/build/libs/parcool-1.20.4-3.4.3.3-forge.jar
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
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.4-loom.mappings.*/*.jar | head -1)
python3 tools/verify_mixins.py "$JAR" common/src/main/java
# checked 224 java files, 52 injection/field targets
# no problems found
```

The checker self-tests first (wrong arity / wrong name must be reported, a real target must stay
silent) and refuses to report anything if that fails — see `tools/README.md`, which also spells out
the 1.20.4 anchor (`PlayerModel#setupAnim(float × 5)`) used by that self-test.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*`, `net.neoforged.*` or `net.minecraftforge.*`. It is wired into `:common:check`, so
`./gradlew build` runs it.

## Verification done for this port

* `./gradlew :common:build` and `./gradlew build` succeed; both distributable jars are produced
  (Fabric 1 189 136 bytes, Forge 1 213 948 bytes).
* `tools/verify_mixins.py` passes: self-test `PASS`, then
  `checked 224 java files, 52 injection/field targets / no problems found` against the resolved
  1.20.4 mojmap merged jar.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: the Fabric jar carries `parcool.accesswidener` (`v2 intermediary`),
  `parcool-common.mixins.json` (12 `common.*` + 11 `client.*` entries, `compatibilityLevel JAVA_17`,
  `injectors.defaultRequire 1`), `fabric.mod.json` (loader `>=0.15.1`, minecraft `~1.20.4`,
  architectury `>=11.0.0`), the `ServiceLoader` file, and intermediary names in its bytecode
  (`class_…` / `method_…`); the Forge jar carries the same assets plus `META-INF/mods.toml` (with
  `mandatory` and `loaderVersion = "[49,)"`), `META-INF/accesstransformer.cfg` with SRG member names,
  the `MixinConfigs: parcool-common.mixins.json` manifest attribute, has no `parcool.accesswidener`
  left, and its bytecode is SRG-remapped (`m_…` / `f_…` member references).
* Both dedicated dev servers were actually booted against this tree (`./gradlew :fabric:runServer`
  with the game directory `fabric/run/` and `./gradlew :forge:runServer` with `forge/run/`, both on
  the default port 25565): Fabric reports `Done (24.999s)!` and Forge reports `Done (25.363s)!`, and
  **both** apply all twelve `common.*` ParCool mixins (`ArgumentTypeInfosMixin`, `EntityMixin`,
  `LevelChunkMixin`, `LivingEntityFallMixin`, `LivingEntityMixin`, `ParCoolDataHolderMixin`,
  `ParCoolDataPersistenceMixin`, `PlayerAttributesMixin`, `PlayerForcedPoseMixin`,
  `PlayerInteractionVisibilityMixin`, `PlayerMixin`, `PotionBrewingMixin`). The Forge log also shows
  the C2S receivers registering (`Registering C2S receiver with id
  parcool:payload.custom_stamina.c2s` …), i.e. the client-only S2C rule does not cost the server any
  of its inbound channels. Both servers were stopped after the boot.

Not verified for this port: a client session on either loader (the mixin verifier covers the eleven
`client.*` targets statically, but no client was booted here), and the optional
Paraglider/EpicFight/BetterThirdPerson/ShoulderSurfing integrations. They compile against the pinned
integration jars and every entry point guards itself at runtime, but none of them was loaded.
