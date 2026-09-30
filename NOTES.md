# NOTES — ParCool on Architectury API for Minecraft 1.21.8

Working notes for this port: the resolved toolchain and where every number came from, each version
delta that had to be decided, every upstream bug found, and what the next port must not trust.

Read-only references: `parcool-Architectury-API-1.21.11` (the base this tree was imported from) and
`parcool-Architectury-API-1.21.1` (the other finished port). Nothing in either tree was changed for
this port; the changes this port would want in them are listed under *Bugs found upstream*.
`parcool-Architectury-API-1.21.7` was **read** as a third reference (see §3) but not modified.

---

## 1. Toolchain, and where each number came from

| | value | source |
|---|---|---|
| `minecraft_version` | `1.21.8` | `https://meta.fabricmc.net/v2/versions/game` — `1.21.8` is a stable release (2025-07-17, a bugfix release on top of 1.21.7). The client jar and `version.json` were resolved by Loom from `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`; `version.json` gives `javaVersion.majorVersion = 21` |
| `neo_version` | `21.8.54` | `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`. NeoForge encodes the target Minecraft in the version (`21.6.x` → 1.21.6, `21.7.x` → 1.21.7, `21.8.x` → 1.21.8); `21.8.54` is the newest release of the 21.8 line (the line stopped at `21.8.54` and moved on to 26.x) |
| `loader_version` (Fabric) | `0.16.14` | the newest Fabric Loader contemporary with this line. Architectury-fabric 17.0.8 asks for `fabricloader >= 0.15.4`; fabric-api 0.136.1+1.21.8 (read out of its own `fabric.mod.json`) asks for `>= 0.16.13`, which 0.16.14 satisfies. Loader 0.19.5 is also listed for 1.21.8 by `meta.fabricmc.net` but was built for the 1.21.11 line, so the contemporary one was taken |
| `fabric_api_version` | `0.136.1+1.21.8` | `https://api.modrinth.com/v2/project/fabric-api/version?game_versions=["1.21.8"]` — the newest `+1.21.8` build |
| `architectury_api_version` | `17.0.8` | `https://maven.architectury.dev/dev/architectury/architectury-fabric/maven-metadata.xml` plus the `fabric.mod.json` out of every candidate jar, cross-checked against Modrinth. **There is no architectury-fabric build whose `fabric.mod.json` says `~1.21.8`**, and 1.21.8 needed a line that was actually *compiled* against a 1.21.8-compatible codebase: 18.x is the 1.21.9 / 1.21.10 line (Modrinth lists 18.0.3 and 18.0.5 for 1.21.9) and 19.0.1 says `~1.21.11`; 17.0.8 is the newest one built on the 1.21.7 codebase, and Modrinth publishes `architectury-api 17.0.8+fabric` for game versions `['1.21.7', '1.21.8']`. `architectury-neoforge 17.0.8` requires MC `[1.21.4,)` / NF `[21.0.110-beta,)`, which 21.8.54 satisfies |
| `dev.architectury.loom` | `1.17.493` | newest published; architectury-plugin 3.5.170 (newest) calls `LoomGradleExtension#disableObfuscation()`, which only exists on the 1.17 line, so the two are not separable |
| `architectury-plugin` | `3.5.170` | newest published |
| `net.neoforged.moddev` | `2.0.148` | newest published. ModDevGradle **2.x** is the branch that supports NeoForge ≥ 21.5; 1.0.x stops at the 21.4 line, so 1.0.x cannot set up 21.8 at all |
| Gradle wrapper | `9.4.1` | required by Loom 1.17 |
| Java toolchain | `21` | `version.json` → `javaVersion.majorVersion = 21`. The system default `java` is 25, which Gradle 9 refuses, so every Gradle invocation here needs `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. That is a property of the machine, not of the build: there is no `org.gradle.java.home` anywhere |

### The `~1.21.7` in architectury-fabric's `fabric.mod.json` is not a problem

architectury-fabric 17.0.8 (and 18.x, and 19.0.1 for that matter) declares
`"minecraft": "~1.21.7"` regardless of the line it was built for, so the predicate looks like it
excludes 1.21.8. It does not: in Fabric Loader's version-predicate syntax `~` is
`SAME_TO_NEXT_MINOR`, i.e. `>= 1.21.7, < 1.22.0`. Verified with `javap -c` on
`fabric-loader-0.19.5.jar`, `net/fabricmc/loader/api/metadata/version/VersionComparisonOperator`:
the enum constant `SAME_TO_NEXT_MINOR` is constructed with the serialized string `"~"` and
`SAME_TO_NEXT_MAJOR` with `"^"`.

### Things that cost time and are worth writing down

* **`./gradlew :common:build` has to run once before `./gradlew build` on a clean checkout.** Architectury
  Loom resolves the `:common` project dependency while it *configures* the loader modules, so on a fresh
  tree `build` dies with `Failed to read metadata from .../common/build/libs/parcool-1.21.8-3.4.3.3.jar`.
  This is inherited from the 1.21.11 tree, not a 1.21.8 bug; the root `build.gradle`'s `bootstrap` task
  covers every case except the truly cold one, and BUILDING.md documents the two-command sequence.
  **Do not "fix" it.**
* **Loom's cache does not key the access widener into the artifact name.** After editing
  `parcool.accesswidener`, `rm -rf .gradle/loom-cache common/build` once, or the old (un-widened)
  Minecraft jar is reused and the widening silently does not happen.
* **A wrong descriptor in the AW is silently ignored** by Loom's class tweaker (no error, no
  widening). Verify with `tools/verify_aw_and_mixins.py`, which diffs the raw jar against the
  AW-applied jar.
* **AW syntax for a constructor is `accessible method <class> <init> <desc>`, not `accessible class`.**
  `accessible class ... <init> ...` fails with
  `ClassTweakerFormatException: Expected (<access> class <className>)`. A nested *class*
  (`BlockEntityType$BlockEntitySupplier`) is widened with `accessible class <fqcn-with-$>` and takes no
  further tokens.
* **NeoForge's AT parser wants `V` on a constructor descriptor**: `public <class> <init>(args)V`.
  A space before `<init>` makes it read the class name as `class<init>`. `tools/AtCheck.java` parses
  this tree's AT with NeoForge's own `AccessTransformerList` (see §5).
* **`minecraft-merged-*-sources.jar` in the Loom cache is decompiled from the AW-applied jar and lies
  about member visibility.** Always decide visibility with `javap` on the class jar.
* Mojang's asset index for 1.21.8 is still `26` (`~/.gradle/caches/fabric-loom/assets/indexes/1.21.8-26.json`),
  the same one 1.21.7 uses — which is why the `sounds.json` check below could be made against it.

---

## 2. Which side of every seam 1.21.8 is on

Everything below was decided by `javap` / by reading the decompiled 1.21.8 sources, never from the
version number. `tools/probe_seams.sh` is the script that produced the table; re-run it after any Loom
cache reset.

| Area | 1.21.8 | evidence | what this port does |
|---|---|---|---|
| `ResourceLocation` vs `Identifier` | **`ResourceLocation`** | `net.minecraft.resources.Identifier` does not exist in the jar | `ResourceLocation` everywhere |
| Attribute registration on NeoForge | **new side** | NeoForge ≥ 21.5 freezes `BuiltInRegistries` before the mod constructors | `:neoforge`'s `NeoForgeAttributes` (NeoForge `DeferredRegister` on the mod event bus, `IEventBus` handed in by the `@Mod` constructor through `ModContainer#getEventBus`); `common/api/Attributes` only *resolves* the holder on NeoForge and is never touched by `ParCool.init()`. Architectury's `DeferredRegister` rejects `Registries.ATTRIBUTE` outright |
| Attribute holder lookup | **new side** | `Registry#get(ResourceLocation)` returns `Optional<Holder.Reference<T>>` | 1.21.11's `orElseThrow` form |
| Entity / BlockEntity save | **new side** | `Entity#save(ValueOutput)` / `#load(ValueInput)`, `BlockEntity#saveAdditional(ValueOutput)` | 1.21.11's forms |
| `BlockEntityType` construction | **neither column** | `javap BlockEntityType`: no `Builder`, and the static `register(String, BlockEntitySupplier, Block…)` is **`private`**; only the package-private 2-arg constructor is reachable | the type is constructed directly from a plain Architectury `DeferredRegister`, with the constructor and the nested `BlockEntitySupplier` widened through AW/AT. `ParCoolPlatform#registerBlockEntityType`, `BlockEntityTypeInvoker` and `setModEventBus` are all absent — no platform seam is needed |
| Render types | **hybrid** | `RenderStateShard` + `CompositeState` + `RenderPipeline` are all there, but `RenderStateShard` has **no** `CULL`/`NO_CULL`/`RENDERTYPE_LEASH_SHADER`, and the only non-private `RenderType#create` is `(String, int, RenderPipeline, CompositeState)` | `client/renderer/RenderTypes` builds its own two pipelines from `RenderPipelines#MATRICES_FOG_SNIPPET`, puts them into `PIPELINES_BY_LOCATION` and wraps them — 5 new AW + 5 new AT entries |
| Entity rendering | **hybrid** | `EntityRenderer<T, S extends EntityRenderState>` has `extractRenderState` + `render(S, PoseStack, MultiBufferSource, int)`; no `SubmitNodeCollector`, no `CameraRenderState`, no `AvatarRenderer` | `ZiplineRopeRenderer` is the render-state shape from 1.21.11 with `submit(…)` replaced by `render(…)` and the buffer written directly |
| Player rendering | **old side** | `net.minecraft.client.model.PlayerModel` (no type parameter) extends `HumanoidModel<PlayerRenderState>`; `PlayerRenderer` / `PlayerRenderState` exist, `AvatarRenderer` / `AvatarRenderState` / `Avatar` do not. The model is still a tree (`getChild("left_sleeve")` for every second-layer part) | `PlayerModelTransformer` keeps the 1.21.11 form; `compat/IPlayerRenderStateEntity` + `PlayerRenderStateEntityMixin` + `PlayerRenderStateExtractorMixin` stand in for the 1.21.11 `Avatar*` trio |
| Key mappings | **old side** | `KeyMapping#MAP` is `Map<InputConstants$Key, KeyMapping>`, `KeyMapping#getCategory()` returns `String`, the constructor is `(String, int, String)` | 1.21.1's `KeyBindings` verbatim, **including `restoreVanillaBindings()`** and the `KeyRecorder#onClientTick` call that drives it, plus the `isPollableKeysym` guard that stops the GLFW `Invalid key -1` spam. Lang key is `key.categories.parcool` |
| Recipe ingredients | **old side** | `unzip -p ~/.gradle/caches/fabric-loom/1.21.8/minecraft-client.jar data/minecraft/recipe/{chain,oak_door,diamond_sword}.json` — plain strings, `"#tag"`, results as `{"id": …}` | 1.21.11's recipe JSONs except `minecraft:chain` (1.21.11's `minecraft:iron_chain` is a 1.21.9+ item) |
| `pack.mcmeta` | **neither column** | `PackMetadataSection`'s codec declares only `description`, `pack_format`, `supported_formats` — **`min_format`/`max_format` do not exist**; `javap -v SharedConstants` gives `RESOURCE_PACK_FORMAT = 64`, `DATA_PACK_FORMAT = 81` | `pack_format: 81` + `supported_formats: [64, 81]`, no min/max |
| Item models | `assets/<ns>/items/<id>.json` | decompiled `ClientItemInfoLoader` (`LISTER = FileToIdConverter.json("items")`); `assets/<ns>/models/item/**` is dead | `assets/parcool/items/*.json` kept, `assets/parcool/models/item/*.json` deleted |
| `Item` description id | **new side** | `BlockItem#getDescriptionId` is gone; `Item#getDescriptionId` is `final` and resolved from the properties | `useBlockDescriptionPrefix()` on the two hook items, so the `block.parcool.*` keys in the eleven language files are the ones used |
| `Item.Properties#setId` | **mandatory** | `javap -c Item$Properties#build`: `Objects.requireNonNull(this.id, "Item id not set")`. Architectury's `DeferredRegister` does not set it | `Items.properties(name)` sets it; `Blocks` sets it on `BlockBehaviour.Properties#setId` and spells the flag `noCollission()` (vanilla's typo; `noCollision` does not exist) |
| `KeyboardInput#tick` | **no arguments** | `javap KeyboardInput` → `public void tick()` (declared on `Input`, inherited) | the handler is `private void parcool$recordKeys(CallbackInfo ci)` with `method = "tick()V"` |
| `InteractionResult` | **interface** | `public interface … { Success SUCCESS; Success SUCCESS_SERVER; }` | 1.21.11's spelling |
| `Input` / `ClientInput` | **record** | `Input` is a record of `forward/backward/left/right/jump/shift/sprint`; `ClientInput.keyPresses` is the `Input` | `KeyBindings` uses `input.keyPresses.forward()` etc. |
| `Entity#hurt` | **new side** | `public final void hurt(DamageSource, float)` + `final boolean hurtOrSimulate` + `abstract boolean hurtServer` | `ZiplineRopeEntity#hurtServer` returns false; `LivingEntityFallMixin` redirects the `void hurt` call |
| `jumpFromGround` | **new side** | on `LivingEntity`, absent from `Player` | `mixin.common.LivingEntityJumpMixin` kept |
| `Entity#isInWaterOrBubble` | **removed** | only `isInWater`, `isInWaterOrRain`, `isUnderWater`, `isEyeInFluid` | `utilities/EntityUtil#isInWaterOrBubble` |
| `Player#canInteractWithEntity` | **still present** | two overloads in `javap Player` | `PlayerVisibilityHandler` documents why `getVisibilityPercent` is the better hook and 1.21.11's mixin is kept |
| `Player#causeExtraKnockback` | **absent** | not in `javap Player`; `javap -c Player#attack` still contains `invokevirtual setSprinting:(Z)V` at offset 635 | `PlayerMixin`'s `@WrapWithCondition` targets `attack` |
| `Commands.LEVEL_GAMEMASTERS` | an **`int`** | `CommandSourceStack#hasPermission(int)`; `permissions()` is gone | `commandSource.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| `CustomRecipe#canCraftInDimensions` | **gone** | `javap CustomRecipe`: only `isSpecial`, `category`, `placementInfo`, `getSerializer` | 1.21.11's `ZiplineRopeDyeRecipe` |
| `BlockBehaviour#onRemove` | **gone** | not in `javap BlockBehaviour` | `ZiplineHookTileEntity#preRemoveSideEffects`, called from `LevelChunk#setBlockState` |
| `Potion` | `new Potion(String name, MobEffectInstance…)` in `net.minecraft.world.item.alchemy` | `javap Potion`; the `name` is what `PotionContents#getName` turns into the lang key | `Potions` passes `"energy_drink"` / `"poor_energy_drink"`, which is what the language files already contain |
| `ItemTintSources` | **exists** at `net.minecraft.client.color.item.ItemTintSources`; `net.minecraft.client.color.item.ItemColor` does **not** | `javap`, decompiled `ItemTintSources` | 1.21.11's `ItemColors` + `ItemTintSourcesAccessor`. Architectury 17.0.8 has no colour handler registry either (checked with `javap` on the jar), so the codec registry is the only route |
| `LevelResource` ctor / `ArgumentTypeInfos#register` | private | `javap` | both widened for the two loaders |
| `GameRenderer#renderLevel` | takes a `DeltaTracker` | `javap` | 1.21.11's explicit descriptor |
| `Level` ctor | no `ProfilerFiller` argument | `javap Level` | 1.21.11's `ClientWorldMixin` |
| `Camera` | **old side**: `getXRot()`/`getYRot()`, `getUpVector()`/`getLeftVector()` | `javap Camera` | 1.21.1's `CameraAnglesMixin` |
| `KeyMapping#key` | still `private` | `javap` | 1.21.1's `KeyMappingMixin` (a `@Shadow` may not widen) |
| `LivingEntityRenderer#shouldShowName` | `protected boolean shouldShowName(T, double)` | `javap` | 1.21.1's mixin, without the `EntityRenderer`/`RenderLayerParent` supertypes 1.21.2 added |
| Sounds | asset index 26 has `entity/leashknot/leash1..3.ogg` (the 1.21.1 `place1..3` are gone) | `1.21.8-26.json` | 1.21.11's `sounds.json` kept, all six vanilla references confirmed present |
| NeoForge mapping naming | **mojmap** | `javap -c` on BetterThirdPerson 6455836 (a shipped NeoForge mod of this era — `versionRange = "[1.21.5,)"` / `"[21.5,)"`) shows `net/minecraft/client/CameraType`, `net/minecraft/client/DeltaTracker` and not a single `m_`/`f_` token | mojmap `accesstransformer.cfg` |

### PROMPT.md corrections made from this port

`PROMPT.md` shipped with this tree, but several of its claims are wrong for 1.21.8 and a handoff
document with wrong lines is worse than no lines, so they were corrected in place:

* the §6 table described 1.21.1 and 1.21.11 as if they were the only two sides; 1.21.8 is a genuine
  three-way hybrid on the three render rows and takes a *mix* of the two columns on nine others —
  every row now says which side 1.21.8 is on;
* the `BlockEntityType` row points at a static `register` that 1.21.8 does not expose (it is private)
  and the `Avatar*` render rows at classes that do not exist;
* the recipe row had the two forms the wrong way round for this version, and the required
  `category` field was not mentioned at all;
* the `pack.mcmeta` row promises `min_format`/`max_format`, which 1.21.8's codec does not have;
* neither `Item.Properties#setId` (without which the mod does not initialise on **either** loader)
  nor `useBlockDescriptionPrefix` is mentioned anywhere, and the `KeyboardInput#tick` handler
  signature is not either;
* §8 (Loom cache trap) told the reader to run `./gradlew --stop`, which this machine's rules forbid;
* §9 told the reader to launch Minecraft, which this tree's brief forbids — the whole phase is
  marked CANCELLED and the definition of done no longer claims two runtime boxes were ticked;
* §12 ticked "both loaders boot into a world", which cannot be ticked here.

## 3. How the sources were assembled

`git archive` of the 1.21.11 tree was unpacked here as the baseline (commit *Import the 1.21.11
Architectury port as the baseline tree*), keeping the module layout, the three `build.gradle` files,
the wrapper, `LICENSE` and `.gitignore`.

The Java sources and the resources were then taken from **`parcool-Architectury-API-1.21.7`**, which
was *read* and not modified. That is a deliberate deviation from "base = the 1.21.11 tree": 1.21.8 is
a pure bugfix release on top of 1.21.7, so its API *is* 1.21.7's API, and re-deriving 232 files from
the 1.21.11 side would have reproduced every one of the seams in the table above in reverse. The
1.21.7 tree is itself a finished port with its own verification notes, and every one of the facts it
relies on was re-derived here against the 1.21.8 jars rather than taken on trust — the table in §2 is
the result. `:common:compileJava` passes unchanged on 1.21.8, which is the strongest single piece of
evidence that the API really is identical.

What was then changed relative to that baseline, all of it version-facing: `gradle.properties`,
`settings.gradle` (MDG version), `common/build.gradle` (the `mixin { defaultRefmapName }` block is
gone — Loom 1.17 does not run the legacy annotation processor and this port ships no refmap),
`neoforge/build.gradle` (the optional-integration jar versions), the two mod descriptors, every
`1.21.7` mention in a comment, the access widener / access transformer comments, and the two
verification tools under `tools/`.

## 4. Mixin set

27 mixins, `injectors.defaultRequire: 1` — `client.AvatarRenderStateEntityMixin` and
`client.AvatarRenderStateExtractorMixin` from 1.21.11 are `PlayerRenderStateEntityMixin` and
`PlayerRenderStateExtractorMixin` here, `common.BlockEntityTypeInvoker` is gone (there is no
`register` left to invoke), and `client.ClientPacketListenerMixin` (1.21.1 only) is not needed.

Every target was checked with `tools/verify_aw_and_mixins.py`, which resolves each `@Mixin` target
through the import list, walks the whole superclass chain with a constant-pool parser (a mixin may
inject a method it only inherits — `KeyboardInput#tick()` lives on `Input`), matches nested classes as
`Outer$Inner`, and balances the parentheses of every annotation's argument list instead of using a
`\(([^)]*)\)` regex:

```
AW entries checked  : 13
mixin files / classes: 27 / 19
mixin injectors     : 36
No problems found.
```

The two `@At` targets that address an *instruction* rather than a member were checked against the
bytecode, not just against `javap`'s member list:

* `LivingEntityFallMixin` → `LivingEntity#causeFallDamage` really contains
  `invokevirtual hurt:(Lnet/minecraft/world/damagesource/DamageSource;F)V` (offset 45).
* `PlayerMixin` → `Player#attack` really contains `invokevirtual setSprinting:(Z)V` (offset 635).

**The verifier is self-tested** (`--self-test` renames one AW entry and every mixin target by an `X`
and must report 37 problems). It is in the tree precisely because the orchestrator's warning about
false "everything verified" reports was real: the first three versions of this script reported
problems that did not exist and missed ones that did.

**This port was never booted** (see §6), so "the target exists with the right descriptor" is as far as
the mixin verification went. A `defaultRequire: 1` wrong target is a hard boot failure, and a
right-name/wrong-overload injection is a silent no-op; neither was exercised at runtime.

## 5. Bugs found upstream, and what this port did

### Taken from the 1.21.1 tree (the 1.21.11 tree is a regression on both) — already present here

* **`ConfigSpec#persist()` writes nothing in the 1.21.11 tree.** `ConfigValue#save()` calls
  `persist()`, which only sets a `dirty` flag that nothing ever reads, so every setting made in the
  in-game screens is lost. This tree has 1.21.1's version, which calls `save(file)`. (The real fix
  would be a debounced writer, which is a redesign, not a port.)
* **`utilities.BufferUtil` regressed in 1.21.11.** `ensureRoom` was made `static` over a `static
  ByteBuffer current` that is only ever assigned by the private constructor — a data race — and the
  overflow checks were *deleted* from `putVector3i`/`putVec3`. This tree has 1.21.1's instance-based
  version, which checks every write and names the payload that overflows.

### Inherited from the 1.21.7 tree, and still true for 1.21.8

* **`RenderTypes` used to be registered only on the first frame.** Its static initialiser is what puts
  the pipelines into `RenderPipelines.PIPELINES_BY_LOCATION`, and `ShaderManager#apply` snapshots and
  precompiles that map during the resource reload. `Renderers.register()` now calls
  `RenderTypes.register()` explicitly. **The 1.21.11 tree still has this bug**; a one-line fix worth
  carrying back.
* **The recipes needed `"category"`.** All five `data/parcool/recipe/*.json` were missing it, and
  vanilla's `ShapedRecipe$Serializer` / `ShapelessRecipe$Serializer` codec declares it as
  `Codec.fieldOf("category")` — re-verified on this version with
  `javap -c 'net.minecraft.world.item.crafting.ShapedRecipe$Serializer'`: `group` and
  `show_notification` go through `optionalFieldOf`, `category` does not. Without it the datapack
  loader reports `Missing field category`, drops the recipe, and **every ParCool item is uncraftable
  in game while the build stays green**. `"category": "misc"` is present in all five here. The same
  omission is in both read-only reference trees and in upstream ParCool; 1.21.7 and 1.21.8 both have
  the fix, so the bug does not reproduce in this port.
* **The 1.21.1 tree's `accesstransformer.cfg` widens the same render members but writes them with SRG
  names** (`f_110147_` for `RENDERTYPE_LEASH_SHADER`), which never match a mojmap-named runtime. This
  tree's AT is mojmap and was parsed with NeoForge's own parser (`tools/AtCheck.java`:
  *"parsed OK, 15 transformers"*; a deliberately broken line is rejected with a syntax error).
* **NeoForge already exposes most of the widened members through its own patches.** All 13 AT entries
  resolve to `public` members in `neoforge/build/moddev/artifacts/neoforge-21.8.54.jar`, so on
  NeoForge the AT is belt-and-braces. It is kept because the NeoForge *production* runtime applies
  only what the mod declares.

### Found, not fixed (upstream behaviour, out of scope for a port)

* `Limitations` filename validation: the `endsWith(".json")` filter is **present and identical in all
  three reference trees and here** — PROMPT.md's claim that the 1.21.11 tree removed it is wrong, and
  it is wrong in this tree's copy of PROMPT.md too. Not touched.
* 26 unused imports. They are the same set the 1.21.11 (27) and 1.21.1 (28) trees carry and they come
  from upstream ParCool. This port added none and removed none; do not sweep them in a port.
* The deprecated-for-removal `NetworkManager.registerReceiver(Side, ResourceLocation, …)` /
  `sendToServer(ResourceLocation, …)` overloads in `NeoForgeParCoolNetwork` (4 javac `[removal]`
  warnings). The `CustomPacketPayload.Type` overloads are the ones Architectury will keep, but mixing
  the two families breaks the `NetworkAggregator.C2S_TYPE`/`S2C_TYPE` maps and the first
  client→server packet is dropped. Switching means one id per direction *and* the type-based send,
  which is a redesign.

## 6. Verification actually performed

* `./gradlew :common:build` then `./gradlew build`: **BUILD SUCCESSFUL**, both artifacts produced
  (`fabric/build/libs/parcool-1.21.8-3.4.3.3-fabric.jar`, `neoforge/build/libs/parcool-neoforge.jar`).
* `:common:checkCommonLoaderIndependence`: passed (it runs as part of `:common:check`).
* `tools/verify_aw_and_mixins.py`: 13 AW entries and 36 mixin injectors verified against the 1.21.8
  jars, self-test included, no problems.
* `tools/verify_artifacts.sh`: all configured mixins are present in both jars, no orphan mixin class
  in either, `parcool.accesswidener` is `accessWidener v2 intermediary` in the Fabric jar, there is no
  refmap in either, mixin targets are remapped to intermediary in the Fabric jar
  (`PlayerMixin` → `class_1657`, `method_7324`, `method_5728`) and stay mojmap in the NeoForge jar
  (`Player`, `attack`, `setSprinting`). Fabric jar: 349 classes (345 `:common` + 4 `:fabric`).
  NeoForge jar: 359 classes.
* Every AW entry verified **applied**: each member is `public` in the AW-applied
  `minecraft-merged-…jar` under `.gradle/loom-cache/minecraftMaven/`, and it was not already public
  in the raw jar (the script reports a no-op entry as a problem).
* `pack.mcmeta` written from the values read out of the jar (`RESOURCE_PACK_FORMAT = 64`,
  `DATA_PACK_FORMAT = 81`), not copied from 34 or 81.
* No `System.out`, no `printStackTrace`, no `TODO`/`FIXME`, no absolute path in any checked-in file
  (the only hits are in generated `build/` output, which is git-ignored).
* Metadata: `fabric.mod.json` → `minecraft: ~1.21.8`, `architectury: >=17.0.8`, `version: 1.21.8-3.4.3.3`;
  `neoforge.mods.toml` → MC `[1.21.8,1.22)`, NF `[21.8.0,)`, architectury `[17.0.8,)`, paraglider
  `[21.5.0,)`, shouldersurfing `[4.11.0,)` (its actual mod version), betterthirdperson `[1.9.0,)`.

## 7. What was **not** verified

The brief for this port forbids launching Minecraft in any form, so the entire runtime half of the
acceptance list is open. None of the following was executed:

* the game booting on either loader, on a dev run or in Prism;
* `Player#createAttributes` resolving the two attributes (the step that dies with
  `Registry is already frozen` if the NeoForge attribute split is wrong for 21.8);
* `grep "GL ERROR"` and `grep "Invalid key"` over a real log;
* every `key.parcool.*` binding being rebindable, and vanilla right-click / Space / Ctrl still working
  alongside ParCool — **this is the `restoreVanillaBindings` check, and 1.21.8 is on the
  one-mapping-per-key side, so it is the one most likely to matter**;
* one action of each family, the settings screen, the stamina HUD, the zipline render;
* two clients on one server, i.e. the `ActionStatePayload` broadcast path;
* the zipline rope's actual pixels — the pipeline registration order (`RenderTypes.register()` from
  `Renderers.register()`) is reasoned from `ShaderManager#apply`, not observed;
* that the recipes actually load in game (the `category` fix is verified against the codec, not
  against a running datapack loader).

## 8. What the next port must not trust

1. **`minecraft-merged-*-sources.jar` lies about visibility.** It is decompiled from the
   AW-applied jar. Use `javap` on the class jar for every access question, and diff the raw jar
   against the AW-applied one (`tools/verify_aw_and_mixins.py` does this).
2. **A wrong descriptor in `parcool.accesswidener` is silently ignored.** Check with the same script;
   do not read "the build is green" as "the widening happened".
3. **The AW is not part of the Loom artifact cache key.** After editing it, delete
   `.gradle/loom-cache` and `*/build`, or the previous (un-widened) jar is reused.
4. **A one-name `@Inject` picks the wrong overload** whenever the target has a bridge method.
   `PlayerModel` has `setupAnim(PlayerRenderState)` plus two bridges, `PlayerRenderer` has
   `setupRotations(PlayerRenderState, …)` plus the `LivingEntityRenderState` one, and
   `LivingEntityRenderer` has two `shouldShowName`. All three mixins use an explicit descriptor here.
5. **A mixin may target a method it only inherits.** `KeyboardInput#tick()` is declared on `Input`.
   A verifier that only reads `javap` on the annotated class will report a false problem.
6. **Vanilla's own recipe files are the ground truth for the recipe format**, not the version number
   and not the neighbouring port. In the 1.21.8 client jar the shaped-recipe `key` is a plain string
   and the result is `{"id": …}`.
7. **The version number does not tell you which side of a seam you are on.** 1.21.7 and 1.21.8 are
   hybrids (render-state entity renderers with `RenderStateShard` render types; `ItemTintSources`
   without `ItemColor`; `ValueInput` without `Identifier`; `Entity#hurt` split without
   `causeExtraKnockback`). `javap` it.
8. **The `parcool.accesswidener` is a `v2 named` file that Fabric Loader only accepts as
   `v2 intermediary` in the distributed jar.** `:common:remapJar` copies it verbatim, which is why
   `:fabric:distJar` drops the common copy and takes the fabric module's.
9. **The one-shot `./gradlew build` on a clean checkout still does not work** (see §1). Run
   `:common:build` first; do not "fix" it.
10. **`~` in a Fabric version predicate is SAME_TO_NEXT_MINOR, not "same patch".** That is why
    architectury-fabric's stale `"minecraft": "~1.21.7"` still admits 1.21.8 — and it is also why
    `"~1.21.4-"` in architectury 16.1.4 admitted 1.21.5.

## 9. Published artifacts

Superseded by the `refCnt: 0` network fix in §10; the jars currently published are:

```
0.1-mc1.21.8fabric-3.4.3.3.jar     f0abeb3ce99816271c1fe37e8a0c45b24dae331ad1c606dc7ed6b109d4fe47b1
0.1-mc1.21.8neoforge-3.4.3.3.jar  e6fee758cc0e4ffde8c84d60c95bcb993a6b91fc19e3ba2e8665eb9e517236a0
```

The pre-fix pair was `21c69b5de817a377af572aae673a90d63583cdc82f0a7c53fac8800921b4d6db` (fabric,
1 205 805 bytes) and `6457dc9ca837dd3d0443aa2bd70846f4ee5d99297e59b9fd78614bf2899be5a9` (neoforge,
1 222 244 bytes), so the artifacts are **not** byte-for-byte identical to what was published before
the fix. Class and entry counts are unchanged (349/511 and 359/527): the only difference is the body
of `FabricParCoolNetwork#register`'s receiver and `NeoForgeParCoolNetwork#register`'s receiver.

copied to `/home/sanufsoii/ports/готовые порты/parcool/`. The clean-checkout build was re-run from
scratch (every `build/`, `.gradle/` and `.architectury-transformer/` deleted): the single
`./gradlew build` fails with the documented `Failed to read metadata from
common/build/libs/parcool-1.21.8-3.4.3.3.jar`, and `./gradlew :common:build && ./gradlew build`
succeeds, after which the three verifiers above were re-run against the freshly produced jars with
the same results.

## 10. The decode ran on a released buffer — `refCnt: 0`

Found by launching a client, not by reading the code, and the faulty line had been carried into
every port from 1.21.7 on. Fixed here in both `FabricParCoolNetwork` and
`NeoForgeParCoolNetwork`, which were byte-identical on this line.

### What the live client said

```
[Server thread/ERROR] (Minecraft) Error executing task on Server
io.netty.util.IllegalReferenceCountException: refCnt: 0
  at io.netty.buffer.AbstractByteBuf.readByte(AbstractByteBuf.java:730)
  at net.minecraft.network.VarLong.read(VarLong.java:28)
  at net.minecraft.network.codec.ByteBufCodecs$8.decode(ByteBufCodecs.java:155)
  at com.alrex.parcool.platform.FabricParCoolNetwork.lambda$register$0(FabricParCoolNetwork.java:48)
[Render thread/WARN] (ParCool) the server limitation snapshot has been missing for 10s,
  so no action can start. Asking the server again (ParCoolIsActive=true).
```

`lambda$register$0` is the receiver lambda registered in `register(...)`; the frames below it are
the payload codec's own `VarLong` read. The second line is the consequence, not a separate bug: the
limitation snapshot never lands, so the action watchdog fires and no action can start.

### Why the decode read freed memory

`NetworkManager.registerReceiver(Side, ResourceLocation, NetworkReceiver)` is Architectury's
deprecated id overload. Its body is `NetworkAggregator#registerReceiver`
(`dev/architectury/impl/NetworkAggregator.java` in the `architectury-18.0.5-sources.jar`; the same
code is in 18.0.8 and 19.0.1):

```java
class_8710.class_9154<BufCustomPacketPayload> type = new class_8710.class_9154<>(id);
...
registerC2SReceiver(type, BufCustomPacketPayload.streamCodec(type), packetTransformers, (value, context) -> {
    class_9129 buf = new class_9129(Unpooled.wrappedBuffer(value.payload()), context.registryAccess());
    receiver.receive(buf, context);
    buf.release();          // <-- the receiver gets a BORROWED buffer with an explicit deadline
});
```

The contract is therefore: the receiver is handed a **borrowed** `RegistryFriendlyByteBuf` that is
released the moment `receive` returns. `class_9129` is an `AbstractByteBuf` in its own right, so its
`release()` drives its own `refCnt` to 0, and any later read trips `ensureAccessible()` →
`IllegalReferenceCountException: refCnt: 0`. (It does not release the buffer it wraps, which is
exactly why the failure is a `refCnt: 0` read rather than a corrupted-payload read.)

This port's receiver threw that contract away:

```java
(buf, context) -> context.queue(() -> handler.accept(erased.decode((RegistryFriendlyByteBuf) buf), context))
```

`context.queue` is `MinecraftServer#execute` / `Minecraft#execute` — it hands the runnable to the
main thread's task queue. The decode therefore did not run until the main thread drained the queue,
which is by definition *after* `receive` returned and *after* `buf.release()`.

`context.queue` is **not** the bug and must stay: it is what makes `handler.accept` thread-safe. The
decode is the only thing that has to move out of it.

### The fix, and the proof in the shipped bytecode

Decode first, on the network thread, while the buffer is alive; queue only the handler:

```java
T payload = erased.decode((RegistryFriendlyByteBuf) buf);
context.queue(() -> handler.accept(payload, context));
```

Before (the previously published NeoForge jar) the queued runnable captured the buffer and the codec,
and `decode` did not exist in the receiver at all:

```
private static void lambda$register$1(BiConsumer, StreamCodec, RegistryFriendlyByteBuf, PacketContext);
   0: aload_3
   5: invokedynamic #44  // InvokeDynamic #1:run:(BiConsumer;StreamCodec;RegistryFriendlyByteBuf;PacketContext;)Runnable
  10: invokeinterface dev/architectury/networking/NetworkManager$PacketContext.queue:(Ljava/lang/Runnable;)V
```

After (rebuilt):

```
private static void lambda$register$1(StreamCodec, BiConsumer, RegistryFriendlyByteBuf, PacketContext);
   2: invokeinterface net/minecraft/network/codec/StreamCodec.decode:(Ljava/lang/Object;)Ljava/lang/Object;
  10: astore        4
  17: invokedynamic #52  // InvokeDynamic #1:run:(BiConsumer;CustomPacketPayload;PacketContext;)Runnable
  22: invokeinterface dev/architectury/networking/NetworkManager$PacketContext.queue:(Ljava/lang/Runnable;)V
```

`decode` at offset 2, `queue` at offset 22, and the queued `Runnable` now captures
`(BiConsumer, CustomPacketPayload, PacketContext)` — no buffer crosses the thread boundary.

This is the shape 1.21.3's `NetworkChannel` path already had: `CHANNEL.register(payload, encoder,
decoder, handler)` takes the decoder as its own lambda, so Architectury invokes it while the buffer
is live and only the handler is queued. 1.21.3 is read-only and was not touched; it is the
correct-pattern reference for this file.

### This class of defect is invisible to the compiler

`./gradlew build` was green with the bug in place and stayed green. Nothing about the types is wrong:
`erased.decode` returns exactly `T`, `context.queue` takes exactly a `Runnable`, and the lambda that
does the wrong thing has precisely the right signature. There is no annotation, no lint and no test
in this tree that can see it, because what is broken is an **ownership/lifetime rule** that lives
entirely inside Architectury's `buf.release()` and is invisible from the call site.

**The only thing that catches it is a live client that has entered a world.** Without a world there
are no incoming ParCool payloads, the receiver lambda is never invoked, the released buffer is never
read, and nothing is ever logged. The defect fires on the *first* packet, so it is latent until
exactly the moment the mod starts doing its job.

A world is necessary but not sufficient: the payload has to actually arrive. A session with no
ParCool limitations configured produces no snapshot to receive and can therefore stay quiet with the
bug fully present. So "it launched cleanly" and even "it got into a world" are both consistent with
the bug being there. Only a session in which a ParCool packet is actually delivered — stamina, action
sync, client settings, limitation snapshot — exercises the decoder.

Corollary for every port: a port whose verification never launched the game has **no evidence either
way** about this, and "it built" must never be written up as "it works".

## Ingredient form and the `chain` / `iron_chain` rename (verified by the orchestrator)

Two claims in the handoff for this port were wrong and are retracted here so the next person
does not re-chase them:

- **The object form `{"item": ...}` for recipe ingredients does not parse on any of these
  versions.** `Ingredient.CODEC` is a holder-set codec (`Codec.either(HolderSetCodec,
  Item.CODEC)`) whose string branch is what a bare `"minecraft:chain"` goes through, and
  `Item.CODEC` is `BuiltInRegistries.ITEM.holderByNameCodec()`. The string form is correct
  from 1.21.1 through 1.21.11. The brief's table row claiming the object form arrived in
  1.21.5 is not reproducible.
- **`minecraft:iron_chain` is not a 1.21.11-only item.** It exists in 1.21.9 and 1.21.10 --
  confirmed in `data/minecraft/recipe/iron_chain.json`, `assets/minecraft/items/iron_chain.json`
  and the textures inside the client resources jar. The vanilla rename `chain` -> `iron_chain`
  landed before 1.21.9. So the correct spelling is version-dependent: `minecraft:chain` for
  1.21.1 through 1.21.8, `minecraft:iron_chain` for 1.21.9 and 1.21.10. This port must use
  whatever its own target's resources jar contains.

`"category": "misc"` is present in all five recipes here. It is a no-op: the codec is
`CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)`, so
`MapCodec#orElse` already defaults it. An earlier note in this file claimed its absence broke
every recipe; that claim was wrong and is retracted.
