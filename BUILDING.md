# Building ParCool (Architectury, Minecraft 1.20.1)

Requires JDK 17. The toolchain is declared through `java { toolchain { languageVersion = 17 } }` in
`build.gradle`; there is no `org.gradle.java.home` and no machine-specific path anywhere in the
build. 1.20.1 predates the Java 21 requirement that starts with 1.20.5, so 17 is the JDK this tree
builds with (and the `"java": ">=17"` its loader metadata asks for).

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.20.1 |
| Architectury API | 9.2.14 |
| Forge | 1.20.1-47.4.28 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.92.12+1.20.1 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.20.1` — the target of this port. Mojang's version manifest lists it as a
  `release`; it is the release upstream ParCool supports with a Forge 47.x branch.
* `forge_version=1.20.1-47.4.28` — from
  `https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`: the newest `47.4.x`
  build for 1.20.1. The build number carries the Minecraft version (`<mc>-<forge>`), and `47.x` is
  the 1.20.1 line. There is **no NeoForge for 1.20.1** — NeoForge starts at 1.20.2 — so the second
  loader is plain Forge here. Upstream ParCool's 1.20.1 branch pins the older `47.4.0`; the mod
  metadata accepts `[47,)`, so both work.
* `loader_version=0.16.14` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`: the newest stable
  `0.16.x`, and it clears the `>=0.16.10` floor that fabric-api 0.92.12 declares.
* `fabric_api_version=0.92.12+1.20.1` — from
  `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`: the last build
  with the `+1.20.1` suffix.
* `architectury_api_version=9.2.14` — from
  `https://maven.architectury.dev/dev/architectury/architectury-forge/maven-metadata.xml`: the newest
  `9.x`. `9.x` is the Architectury line for Minecraft 1.20/1.20.1 — the Fabric half of the same
  version declares `minecraft: "~1.20-"` in its `fabric.mod.json`, so one property covers both
  loaders. `10.x` and later belong to the newer 1.20 releases (the sibling 1.20.4 tree pins
  `11.1.17`).
* `dev.architectury.loom 1.7.435` and `architectury-plugin 3.5.170` — inherited from the 1.21.1 base
  tree; both resolve for 1.20.1 (the build below proves it). Loom prints "You are using an outdated
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

## Port notes (1.21.1 → 1.20.1)

This tree started as a copy of the finished 1.21.1 Architectury tree (first commit `Import the
ParCool Architectury 1.21.1 tree as the base for the 1.20.1 port`) and was retargeted to 1.20.1.
1.20.1 is five releases below the base tree, so this is the reference document for the whole
pre-1.20.2 API generation; the 1.20 port inherits it, and 1.20.2/1.20.3/1.20.4 inherit it plus the
deltas their own BUILDING.md files list.

### API the base tree used and 1.20.1 does not have

* **Item data components (1.20.5) are NBT here.** `ZiplineRopeItem` keeps the rope state in the
  stack's tag (`ItemStack#getOrCreateTag` / `CompoundTag`), `ZiplineRopeDyeRecipe` reads and writes
  the same tag, and the two `common/item/component/Zipline*Component` classes the 1.21.1 tree had are
  deleted.
* **`DeltaTracker` (1.20.5) is a plain `float` here,** and `Timer` exposes `partialTick` as a field
  (`Minecraft#getFrameTime()` is the equivalent accessor). See `api/client/gui/ParCoolHUDEvent`,
  `client/animation/PlayerModelTransformer` and `GameRendererTickMixin`, which injects into 1.20.1's
  `GameRenderer#renderLevel(float, long, PoseStack)`.
* **`ItemInteractionResult` and the item-first `Block#use` signature (1.20.2) do not exist.**
  1.20.1 blocks return `InteractionResult` and the held stack comes off the hand the player used —
  see `ZiplineHookBlock#use`. The same class replaced the Forge-only
  `BlockBehaviour#getPistonPushReaction` override with vanilla's
  `Properties#pushReaction(PushReaction.DESTROY)`, which `BlockState` now reports.
* **`Player#canPlayerFitWithinBlocksAndEntitiesWhen` does not exist at all.** Its body lives in the
  protected `Entity#canEnterPose`, so `Crawl` spells the standing-space check out itself
  (`Crawl#parcool$canFitStanding`) and the access widener carries no line for it.
* **Attribute modifiers are keyed by `UUID`** (`AttributeModifier(UUID, String, double, Operation)`);
  the `ResourceLocation`-keyed form is 1.21 API. See `FastRun`, `FastSwim` and
  `client/action/ClientActionProcessor`.
* **The visibility hook is `LivingEntity#getVisibilityPercent(Entity)`** — the value a viewer assigns
  to an entity target. 1.20.2+ moved the equivalent hook to `Player#canInteractWithEntity`, which is
  what the newer ports mix into; that method does not exist here yet, so
  `PlayerInteractionVisibilityMixin` targets `LivingEntity` instead and multiplies the returned value
  by 0.1 for hidden players.
* **`PlayerRenderer#setupRotations` still has the five-argument form**: the trailing `scale`
  parameter arrives with 1.20.2, so the descriptor ends in `FFF`, not `FFFF`.
* **`PotionBrewing` has no builder.** 1.20.1 keeps its mixes in a plain static list filled by
  `PotionBrewing#bootStrap` and `#addMix` is private, so the 1.21.1 `PotionBrewingBuilderMixin` is
  replaced by `PotionBrewingMixin` (an `@Invoker` plus an inject into the static bootstrapping
  method). `ParCoolBrewingRecipe` documents the same difference.
* **`BlockEntity` save/load take no `HolderLookup.Provider`** — `saveAdditional(CompoundTag)` and
  `load(CompoundTag)`, as `ZiplineHookTileEntity` implements them. `BlockEntity#onChunkUnloaded()` is
  a NeoForge addition, so `LevelChunkMixin` injects the unload notification instead.
* **Recipes have their own id and category**: `CustomRecipe(ResourceLocation, CraftingBookCategory)`.
  The data pack keeps the pre-1.21 layout (`data/parcool/recipes/`, `loot_tables/`, `tags/blocks/`,
  one file per tag) and `pack.mcmeta` is `"pack_format": 15`.
* **GUI:** `Checkbox` has a public constructor (no builder, no `setHeight`), `AbstractWidget#setHeight`
  does not exist before 1.20.2, `Screen#render` already runs `renderBackground` in the order the
  1.21.1 tree had to force, scrolling takes a single delta (no `scrollX`/`scrollY` split), `DyeColor`
  only exposes float RGB components (`getTextureDiffuseColors()`; the packed-int accessor is 1.21.x)
  and `FastColor.ARGB32#color` has no one-argument overload. See `client/gui/*` and
  `ZiplineHookTileEntity`/`CreativeTabs`.
* **No `Codec` / `StreamCodec`:** `ReadonlyStamina` reads and writes fields directly, `StaminaType`
  lost its `StreamCodec` and the whole `common/network` package was rewritten on
  `FriendlyByteBuf` (`MessageType`, `ParCoolMessage`, `PayloadBuffers` replace
  `CustomPacketPayload` + `StreamCodec` + `ListStreamCodec`).
* **`ResourceLocation` still has public constructors** (`new ResourceLocation(namespace, path)`);
  `ResourceLocation.fromNamespaceAndPath` is 1.21 API. Registry entries are read through
  `RegistrySupplier#get()`, and `EntityDimensions` is a class with plain fields
  (`getDimensions(Pose).height`), not a record.
* **`MixinExtras` is not part of the Forge 1.20.1 runtime** (nothing named `mixinextras` is on the
  dev classpath), so `PlayerMixin`'s sprint veto uses vanilla's `@Redirect` where the 1.21.1 tree used
  `@WrapWithCondition`.
* **`jumpFromGround` ordering:** 1.21.1 sets `setDeltaMovement(x, getJumpPower(), z)` (absolute) and
  the loaders disagreed about when the injection runs relative to it, so `ChargeJump` adds its
  impulse on the first airborne tick instead of inside the hook.

### The attribute ordering trap (1.20.1-only)

`PlayerAttributesMixin` adds ParCool's two attributes to the builder `Player#createAttributes`
assembles, and it adds the **raw** `Attributes.MAX_STAMINA_ATTRIBUTE` /
`STAMINA_RECOVERY_ATTRIBUTE` instances instead of the registry entries. The reason is an ordering
trap unique to this Minecraft version:

* `Player#createAttributes` is called from `DefaultAttributes`' *static initialiser*, which
  `Bootstrap#validate` triggers at the end of the bootstrap pass — i.e. while the game is still
  starting.
* Fabric invokes mod entry points *after* `Bootstrap#bootStrap`/`Bootstrap#validate`, so neither
  loader's `ParCool#init` has run when the mixin fires. Reading `Attributes.MAX_STAMINA.get()` there
  is a hard `NullPointerException: Registry Object not present: parcool:max_stamina` — exactly how
  this port first failed to boot.

Adding the raw instances sidesteps it completely: the objects exist as soon as `Attributes` is
initialised, and the same objects are what the `DeferredRegister` hands to the registry later
(`RegisterEvent` on Forge, the loader entry point on Fabric, both still before any world exists). On
1.21.1 the two entries had to be registered by hand from the loader entry point (NeoForge's `Registry`
refuses writes from the shared code and Architectury's `DeferredRegister` rejected
`Registries.ATTRIBUTE`); on 1.20.1 `minecraft:attribute` is a normal Forge registry, so
`DeferredRegister` works and `ParCool#init` calls `Attributes.registerAll()` first of all.

### Server→client receivers are registered in the client environment only

Architectury's Fabric backend marks its S2C registrar `@Environment(EnvType.CLIENT)`. Fabric Loader
strips that member when the game runs as a dedicated server, and the shared
`NetworkManager#registerReceiver(Side.S2C, …)` then dies with
`NoSuchMethodError: …registerS2CReceiver(…)` while the server is still starting — which is how this
port's first dedicated-server boot failed. `NetworkRegistries#registerPayloads()` therefore registers
the C2S receivers unconditionally and the S2C receivers only when
`Platform#getEnvironment() == Env.CLIENT`. Skipping on a dedicated server costs nothing: a
client-bound receiver can only ever fire on a client, and the Forge backend keeps its S2C receivers
in a plain map that nothing reads server-side.

### Networking and the platform seam

Architectury 9.x exposes the channel-name `NetworkManager` API (`registerReceiver(id, …)` per
direction, `sendToServer` / `sendToPlayer` / `sendToPlayers(id, buffer)`), which is exactly what both
1.20.1 loaders want. The per-loader networking seam of the 1.21.1 tree (`platform/ParCoolNetwork`,
`FabricParCoolNetwork`, `NeoForgeParCoolNetwork`) is therefore gone; `NetworkRegistries` is the single
entry point. The payload ids keep the upstream `parcool:payload.*` names for the S2C direction; the
C2S direction of the three bidirectional messages uses the same name with a `.c2s` suffix, so a
mismatched jar is rejected with an unknown-payload disconnect.

`ParCoolPlatform` gained the pieces the 1.21.1 tree got from loader APIs that do not exist here:

* `createDamageSource(Player, ResourceKey<DamageType>)` is a default method: neither loader exposes a
  shortcut on 1.20.1 (`DamageSources#source(ResourceKey)` is private and Forge adds no replacement),
  so the holder is looked up out of the player's `registryAccess()`.
* Gravity: 1.20.1 has no `Attributes.GRAVITY` at all (it appears in 1.21). Gravity is the hard-coded
  `LivingEntity.DEFAULT_BASE_GRAVITY`, which Forge turns into `forge:entity_gravity`; `getGravity`
  falls back to the constant where a loader has no attribute.
* `getSwimSpeedAttribute` returns Forge's `forge:swim_speed` (the base tree's `neoforgeswim_speed`
  does not exist on this line), `null` on Fabric.
* `initClientIntegrations()` is the seam through which the Forge-only integrations install their
  client hooks (the Paraglider HUD and animation bridges) — `AdditionalMods` calls it on the client.

### Loader modules

* **`fabric`**: `ParCoolFabric` / `ParCoolFabricClient` entry points, `FabricParCoolPlatform`, and
  the `ServiceLoader` file that binds the platform. `fabric.mod.json` declares `minecraft ~1.20.1`,
  `architectury >=9.0.0`, `java >=17` and suggests Patchouli/ShoulderSurfing.
* **`forge`**: a single `@Mod(ParCool.MOD_ID)` class (`ParCoolForge`). Its constructor has to hand the
  mod event bus to Architectury (`EventBuses.registerModEventBus`) *before* calling
  `ParCool.init()` — the first `DeferredRegister.create(...)` in `:common` resolves the bus through
  that lookup and throws `Can't get event bus for mod 'parcool'` until it has run. The registration
  itself happens later, when Forge fires `RegisterEvent` on the bus (which is also why nothing in
  `:common` may resolve a `RegistrySupplier` during construction). The client half
  (`ParCoolForgeClient`) is reached through `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)`, because
  1.20.1's `@Mod` has no `dist` member — that is a NeoForge 1.20.2+ addition, so this cannot be a
  second `@Mod` class the way the 1.21.1 tree's NeoForge module does it. `ForgeParCoolPlatform`
  supplies the loader side (swim speed / gravity attributes, damage sources, client integrations) and
  the optional integrations are wired the same way (Paraglider 20.1.3, EpicFight 20.14.17,
  BetterThirdPerson 1.9.0, ShoulderSurfing 1.20.1-4.11.0).
  The integrations are pinned as `curse.maven:` coordinates in an isolated
  `parcoolIntegrationsCompile` configuration: their jars are SRG-named with their own mappings, and
  putting them on Loom's normal configurations makes Loom try to merge those mappings into
  `:common` and abort with "Mapping source name conflicts detected". Every entry point checks that
  the mod is loaded *and* that its API classes resolve, so an absent integration degrades to
  ParCool's own stamina/camera behaviour.
* **Forge metadata:** `META-INF/mods.toml` uses the 1.20.1 dependency spelling
  `mandatory = true|false` — Forge 47 rejects a dependency without it
  (`InvalidModFileException: Missing required field mandatory in dependency`), and the newer
  `type = "required"|"optional"` spelling only exists from 1.20.2 on. There is also **no
  `[[mixins]]` section** on this line (it arrives with 1.20.2): the config is announced through the
  jar manifest's `MixinConfigs` attribute, which the `:forge` `Jar` tasks set, and the access
  transformer is auto-loaded from `META-INF/accesstransformer.cfg` because `mods.toml` does not list
  `accessTransformers`.

### Mixin set

12 `common.*` + 11 `client.*` mixins, i.e. 224 Java files / 52 injection targets as the verifier
below counts them. Two changes against the 1.21.1 tree:

* `common.PotionBrewingBuilderMixin` became `common.PotionBrewingMixin` (no builder on this line).
* `client.ClientPacketListenerMixin` was dropped: both of its `@Inject`s had empty bodies in the base
  tree (leftovers of a removed feature) and 1.20.1's `ClientPacketListener` has no
  `ClientCommonPacketListenerImpl` superclass to mirror, so there was nothing to port.

`parcool-common.mixins.json` declares `"compatibilityLevel": "JAVA_17"` and `"defaultRequire": 1`, so
a target that stops matching is a hard boot failure rather than a silent no-op.

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
# -> fabric/build/libs/parcool-1.20.1-3.4.3.3-fabric.jar
# -> forge/build/libs/parcool-1.20.1-3.4.3.3-forge.jar
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
0.1-mc1.20.1fabric-3.4.3.3.jar   # = fabric/build/libs/parcool-1.20.1-3.4.3.3-fabric.jar
0.1-mc1.20.1forge-3.4.3.3.jar    # = forge/build/libs/parcool-1.20.1-3.4.3.3-forge.jar
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
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-*/*.jar | head -1)
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
  1.20.1 mojmap jar.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: the Fabric jar carries `parcool.accesswidener`, `parcool-common.mixins.json`,
  `fabric.mod.json` (`"version": "1.20.1-3.4.3.3"`), the `ServiceLoader` file, and intermediary names
  in its bytecode (`class_…` / `method_…` in the mixins and the mod classes); the Forge jar carries
  the same assets plus `META-INF/mods.toml` (with `mandatory` and `loaderVersion = "[47,)"`) and
  `META-INF/accesstransformer.cfg`, has no `parcool.accesswidener` left, and its bytecode is
  SRG-remapped (`m_…` member references and SRG mixin annotation strings).
* Both dedicated dev servers were actually booted against this tree (`./gradlew :fabric:runServer`
  with the game directory `fabric/run/` and `./gradlew :forge:runServer` with `forge/run/`, both on
  the default port 25565): Fabric reports `Done (27.360s)!` and Forge reports `Done (24.716s)!`, and
  **both** apply all twelve `common.*` ParCool mixins (`ArgumentTypeInfosMixin`, `EntityMixin`,
  `LevelChunkMixin`, `LivingEntityFallMixin`, `LivingEntityMixin`, `ParCoolDataHolderMixin`,
  `ParCoolDataPersistenceMixin`, `PlayerAttributesMixin`, `PlayerForcedPoseMixin`,
  `PlayerInteractionVisibilityMixin`, `PlayerMixin`, `PotionBrewingMixin`). The Forge log also
  shows the C2S receivers registering (`Registering C2S receiver with id
  parcool:payload.custom_stamina.c2s` …), i.e. the client-only S2C rule above does not cost the
  server any of its inbound channels. Both servers were stopped after the boot.

Not verified for this port: a client session on either loader (the mixin verifier covers the eleven
`client.*` targets statically, but no client was booted here), and the optional
Paraglider/EpicFight/BetterThirdPerson/ShoulderSurfing integrations. They compile against the pinned
integration jars and every entry point guards itself at runtime, but none of them was loaded.
