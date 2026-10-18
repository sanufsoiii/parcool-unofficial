# Building ParCool (Architectury, Minecraft 1.20.6)

Requires JDK 21. The toolchain is declared through `java { toolchain { languageVersion = 21 } }` in
`build.gradle` and `org.gradle.jvmargs` in `gradle.properties`; there is no `org.gradle.java.home`
and no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`, each read from the publisher's own
metadata rather than guessed):

| | |
|---|---|
| Minecraft | 1.20.6 |
| Architectury API | 12.1.4 |
| NeoForge | 20.6.144 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.100.8+1.20.6 |
| Architectury Loom | 1.7.435 |
| architectury-plugin | 3.5.170 |
| ModDevGradle | 1.0.24 |
| Gradle | 8.10.2 |
| Mappings | official Mojang mappings (`loom.officialMojangMappings()`) |

Where each number comes from:

* `minecraft_version=1.20.6` — the target of this port; Mojang's version manifest lists it as a
  `release`, and it is the last 1.20.x release.
* `neo_version=20.6.144` — `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`,
  the newest of the `20.6.*` line. NeoForge's version number *is* the Minecraft version it targets,
  so `20.6.x` is 1.20.6, `21.0.x` is 1.21 and `21.1.x` is 1.21.1 (the base tree).
* `fabric_api_version=0.100.8+1.20.6` — `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`,
  the last build with the `+1.20.6` suffix. Its own `fabric.mod.json` asks for
  `fabricloader >=0.15.6` and `minecraft >=1.20.5- <1.20.7-`.
* `loader_version=0.16.14` — `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`,
  the newest `0.16.x`; it satisfies the `>=0.15.6` that fabric-api declares.
* `architectury_api_version=12.1.4` — `https://maven.architectury.dev/dev/architectury/architectury-neoforge/maven-metadata.xml`;
  `architectury-neoforge 12.1.4` declares `minecraft [1.20.6,)` and `neoforge [20.6.98-beta,)`,
  which 20.6.144 satisfies. `12.0.x` is the 1.20.5 line and `13.x` is the 1.21 line.
* `dev.architectury.loom 1.7.435` and `architectury-plugin 3.5.170` — inherited from the 1.21.1 base
  tree; both resolve for 1.20.6 (the build below proves it). Loom prints
  "You are using an outdated version of Architectury Loom!" on every configuration; that is expected
  for this Minecraft and not an error.
* `net.neoforged.moddev 1.0.24` — bumped from the base tree's `1.0.9`. The `1.0.x` line is the one
  that still supports the NeoForge 20.x module layout, and `1.0.24` was verified to configure and
  compile against NeoForge `20.6.144` before the port; `1.1.x` and later assume the 21.x layout.
* The `parchment_minecraft_version`, `parchment_mappings_version` and `yarn_mappings` leftovers the
  1.21.1 tree carried are **dropped** here, as in the neighbouring 1.21 port: nothing in the build
  ever read them and the project is mojmap-only.

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

## Port notes (1.21.1 → 1.20.6)

1.20.6 sits two API generations below the 1.21.1 base tree, so unlike the 1.21 port this one has real
source deltas. They are all straight version seams; nothing about the mod's behaviour changed.

* **`ResourceLocation` constructors.** `fromNamespaceAndPath` / `parse` / `tryParse` only exist from
  1.21; on 1.20.6 the constructors are still public. Every id creation in `:common`, `:fabric` and
  `:neoforge` (`Attributes`, `SoundEvents`, `NetworkRegistries` and all payload types, `BlockTags`,
  `DamageSources`, `ColorUtil` users, `FabricParCoolNetwork`, `ParCoolPlayerStates`) uses
  `new ResourceLocation(namespace, path)` / `new ResourceLocation(string)` again.
* **Attribute modifiers are keyed by `(UUID, String name)`.** The `ResourceLocation`-keyed
  `AttributeModifier` (and `AttributeInstance#getModifier(ResourceLocation)`) is 1.21+. `FastRun`,
  `FastSwim` and the exhausted-slowness modifier derive the UUID with `UUID.nameUUIDFromBytes` from
  the *same* string the 1.21.1 tree used as the id (`"parcool:modifier.speed.fastrun"`,
  `"parcool:exhausted.speed"`), so the modifier keeps one stable identity instead of a fresh random
  UUID per launch, which would stack up across restarts.
* **`DeltaTracker` does not exist yet.** 1.20.6 hands render callbacks a plain `float partial tick`
  (`ClientGuiEvent.RENDER_HUD`), so `ParCoolHUDEvent.RenderEvent`, `HUDManager`, `HUDRegistry` and
  the stamina HUD carry `float`; `PlayerModelTransformer` uses `Minecraft#getFrameTime()` where
  1.21.1 used `getTimer().getGameTimeDeltaPartialTick(false)`. The base tree's comment about the
  limbs and the body rotation interpolating against the same clock is preserved — both sides now
  read the same `Timer.partialTick`.
* **`VertexConsumer` still has the old builder names.** `vertex(Matrix4f, x, y, z)` / `color(...)` /
  `uv2(light)` instead of `addVertex` / `setColor` / `setLight`; `ZiplineRopeRenderer` (the whole
  rope geometry) was renamed mechanically. As the base tree notes, a wrong name here would be a
  silent no-op at runtime, not a compile error.
* **`DyeColor#getTextureDiffuseColor()` is 1.21+.** 1.20.6 exposes the float triplet
  `getTextureDiffuseColors()`. `ColorUtil#getTextureColor` packs it back through
  `FastColor.ARGB32`, which is what rope dyeing and the creative-tab preview use.
* **Crafting input.** 1.20.6's `SpecialRecipe#matches`/`#assemble` receive a `CraftingContainer`
  with `getContainerSize()`; `CraftingInput`/`size()` is the 1.21 rename
  (`ZiplineRopeDyeRecipe`).
* **`ItemStack` from a `RegistrySupplier`** is `new ItemStack(Items.ZIPLINE_ROPE.get())`; the
  1.21.1 tree's `new ItemStack(Items.ZIPLINE_ROPE::get)` needs a constructor 1.20.6 does not have.
* **Data directories use the pre-1.21 names:** `data/parcool/recipes/`, `data/parcool/loot_tables/`
  and `data/parcool/tags/blocks/`. 1.21 renamed them to `recipe/`, `loot_table/` and
  `tags/block/`; the 1.21.1 base tree therefore moved them, and this port moves them back.
  The recipe JSON syntax is the same plain `{"ingredients": [...]}` form on both versions.
* **`pack.mcmeta`** is `"pack_format": 32, "supported_formats": [32, 41]` — resource pack format 32
  and data pack format 41 for the 1.20.5/1.20.6 line (1.21 is 34/48).
* **`loaderVersion` is `[3,)`, not `[4,)`.** NeoForge 20.6 implements `javafml` 3 (`3.0.45` in the
  launch log) while the 21.0 line implements 4. Declaring `[4,)` aborts the boot with
  `fml.language.missingversion ... found 3.0.45`; this was caught by actually booting the NeoForge
  server.
* **Access transformer** (`neoforge/src/main/resources/META-INF/accesstransformer.cfg`): NeoForge
  dropped SRG runtime names in the 1.20.5 line, so the base tree's `f_110147_`-style entries do not
  resolve and the file is written in Mojang names (`RenderStateShard NO_TEXTURE`, `Entity onGround`,
  `ArgumentTypeInfos register`, `Player canPlayerFitWithinBlocksAndEntitiesWhen`,
  `LevelResource <init>`, `LivingEntity swimAmount[O]`) — the same members the Fabric access widener
  widens.
* **Loader metadata ranges:** `fabric.mod.json` — `minecraft ~1.20.6`, `architectury >=12.0.0`;
  `neoforge.mods.toml` — `neoforge [20.6,)`, `minecraft [1.20.6,1.21)`, `architectury [12.0.0,)`.
* **Optional integrations:** none of Paraglider, EpicFight, ShoulderSurfing or BetterThirdPerson
  published a 1.20.5/1.20.6 build — their lines jump from the 1.20.x era to 1.21 — so the pins stay
  on the 1.21.1-era builds and the entry points guard themselves at runtime (see the comment block in
  `neoforge/build.gradle`).
* The mixin set is unchanged: all 12 `common.*` and 12 `client.*` targets resolve against the 1.20.6
  jar (proven below by the verifier). `parcool-common.mixins.json` keeps
  `"compatibilityLevel": "JAVA_21"`; Fabric Loader logs that this exceeds the maximum level
  sponge-mixin 0.8.7 supports (`JAVA_13`) and selects the config anyway — the mixins apply, as the
  server boots below show.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.20.6-3.4.3.3-fabric.jar
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

The two jars are distributed under these names:

```
0.1-mc1.20.6fabric-3.4.3.3.jar      # = fabric/build/libs/parcool-1.20.6-3.4.3.3-fabric.jar
0.1-mc1.20.6neoforge-3.4.3.3.jar    # = neoforge/build/libs/parcool-neoforge.jar
```

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runClient    :neoforge:runServer
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin. A dedicated server refuses to start until the EULA is acknowledged, so before the
first `:fabric:runServer` / `:neoforge:runServer` create `fabric/run/eula.txt` (or
`neoforge/run-server/eula.txt` — the NeoForge runs use their own game directory) containing:

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
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.6-*/*.jar | head -1)
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
  1.20.6 mojmap jar.
* `:common:checkCommonLoaderIndependence` passes as part of `build`.
* Jar contents checked: the Fabric jar carries `parcool.accesswidener` in `v2 intermediary`,
  `parcool-common.mixins.json`, `fabric.mod.json`, the ServiceLoader file and `ParCool` +
  `ParCoolFabric` + `FabricParCoolPlatform`; the NeoForge jar carries the same plus
  `META-INF/neoforge.mods.toml`, `META-INF/accesstransformer.cfg`, `ParCoolNeoForge` and
  `NeoForgeParCoolPlatform`.
* Both dedicated dev servers were actually booted against this tree
  (`./gradlew :fabric:runServer` with the game directory `fabric/run/` and `./gradlew
  :neoforge:runServer` with `neoforge/run-server/`, both on the default port 25565, one after the
  other): Fabric reports `Done (8.573s)!` and NeoForge reports `Done (10.809s)!`, and **both** apply
  all twelve `common.*` ParCool mixins (`ArgumentTypeInfosMixin`, `EntityMixin`, `LevelChunkMixin`,
  `LivingEntityFallMixin`, `LivingEntityMixin`, `ParCoolDataHolderMixin`, `ParCoolDataPersistenceMixin`,
  `PlayerAttributesMixin`, `PlayerForcedPoseMixin`, `PlayerInteractionVisibilityMixin`, `PlayerMixin`,
  `PotionBrewingBuilderMixin`). Both servers were stopped after the boot.

Not verified for this port: a client session on either loader for 1.20.6 (the mixin verifier covers
the twelve `client.*` targets statically, but no client was booted here), and the optional
Paraglider/EpicFight/BetterThirdPerson integrations. They compile against the pinned integration
jars and every entry point guards itself at runtime, but none of them was loaded.
