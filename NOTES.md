# NOTES — ParCool on Architectury API for Minecraft 1.21.7

Working notes for this port: the resolved toolchain and where every number came from, each version
delta that had to be decided, every upstream bug found, and what the next port must not trust.

The two finished reference ports are `parcool-Architectury-API-1.21.1` (old side of every seam) and
`parcool-Architectury-API-1.21.11` (new side). Both are read-only. Nothing in either tree was changed
for this port; the changes this port would want in them are listed under *Bugs found upstream*.

---

## 1. Toolchain, and where each number came from

| | value | source |
|---|---|---|
| `minecraft_version` | `1.21.7` | `https://meta.fabricmc.net/v2/versions/game` — `1.21.7` is a stable release; the client jar + `version.json` were resolved by Loom from `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` |
| `neo_version` | `21.7.25-beta` | `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`. NeoForge encodes the target Minecraft in the version (`21.6.x` → 1.21.6, `21.7.x` → 1.21.7, `21.8.x` → 1.21.8); `21.7.25-beta` is the newest release of the 21.7 line |
| `loader_version` (Fabric) | `0.16.14` | newest Fabric Loader of the 1.21.7 era, from the local Gradle cache. Architectury-fabric 18.0.8 asks for `fabricloader >= 0.15.4` |
| `fabric_api_version` | `0.128.2+1.21.7` | `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` — the last `+1.21.7` build. Architectury-fabric 18.0.8 asks for `fabric-api >= 0.127.0` |
| `architectury_api_version` | `18.0.8` | `https://maven.architectury.dev/dev/architectury/architectury-fabric/maven-metadata.xml`, then `fabric.mod.json` out of each candidate jar. **18.x is the last line that still targets 1.21.x**: 18.0.8 declares `minecraft: ~1.21.7`; 19.0.1 is `~1.21.11` and 20.x has moved to 26.1. 17.0.8 is the same target but older. `architectury-neoforge 18.0.8` requires MC `[1.21.4,)` / NF `[21.0.110-beta,)`, which 21.7.25-beta satisfies |
| `dev.architectury.loom` | `1.17.493` | `https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml` — newest published. architectury-plugin 3.5.170 (newest) calls `LoomGradleExtension#disableObfuscation()`, which only exists on the 1.17 line, so the two are not separable |
| `architectury-plugin` | `3.5.170` | `https://maven.architectury.dev/architectury-plugin/architectury-plugin.gradle.plugin/maven-metadata.xml` — newest published |
| `net.neoforged.moddev` | `2.0.148` | `https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml` — newest published. ModDevGradle **2.x** is the branch that supports NeoForge ≥ 21.5; 1.0.x stops at the 21.4 line, so 1.0.x cannot set up 21.7 at all |
| Gradle wrapper | `9.4.1` | required by Loom 1.17 |
| Java toolchain | 21 | `1.21.7/version.json` → `javaVersion.majorVersion = 21`; also the only JDK the system default path offers for Gradle 9 (system `java` is 25, which Gradle 9.x refuses) |

### Things that cost time and are worth writing down

* **`./gradlew :common:build` has to run once before `./gradlew build` on a clean checkout.** Architectury
  Loom resolves the `:common` project dependency while it *configures* the loader modules, so on a fresh
  tree `build` dies with
  `Failed to read metadata from .../common/build/libs/parcool-1.21.7-3.4.3.3.jar`. This is inherited
  from the 1.21.11 tree, not a 1.21.7 bug; the root `build.gradle`'s `bootstrap` task covers every case
  except the truly cold one, and BUILDING.md documents the two-command sequence. **Do not "fix" it.**
* **Loom's cache does not key the access widener into the artifact name.** After editing
  `parcool.accesswidener`, `rm -rf .gradle/loom-cache common/build` once, or the old (un-widened)
  Minecraft jar is reused and the widening silently does not happen. The symptom is a compile error
  that mentions `private`/`protected` on a member that the AW claims to be public.
* **A wrong descriptor in the AW is silently ignored** by Loom's class tweaker (no error, no widening).
  `Player#canPlayerFitWithinBlocksAndEntitiesWhen` took `Lnet/minecraft/world/phys/Pose;` here first and
  has to be `Lnet/minecraft/world/entity/Pose;` — the parameter moved packages in 1.21.2. Verify with
  `javap` on the *AW-applied* jar, not on the plain Mojang jar.
* **NeoForge's AT parser wants `V` on a constructor descriptor**: `public <class> <init>(args)V`.
  `...<init>(args)` fails with `Invalid method descriptor '(...)' at line N`, and
  `...<init> (args)V` (a space before `<init>`) makes it read the class name as `class<init>`.
  Verified against `neoforge-21.7.25-beta-userdev.jar`'s own `ats/accesstransformer.cfg`.
* **AW syntax for a constructor is `accessible method <class> <init> <desc>`, not `accessible class`.**
  `accessible class ... <init> ...` fails with
  `ClassTweakerFormatException: Expected (<access> class <className>)`.
* **`minecraft-merged-*-sources.jar` in the Loom cache is decompiled from the AW-applied jar and lies
  about member visibility** — it shows `RenderPipelines.PIPELINES_BY_LOCATION` as `public static final`
  while the real class has it `private`. Always decide visibility with `javap` on the class jar.
* Mojang's asset index for 1.21.7 (`26`) has been pruned from `piston-meta` (HTTP `BlobNotFound`).
  `~/.gradle/caches/fabric-loom/assets/indexes/1.21.8-26.json` is the same index and is what the
  `sounds.json` check below was made against.

---

## 2. Which side of every seam 1.21.7 is on

Everything below was decided by `javap` / by reading the decompiled 1.21.7 sources in
`~/.gradle/caches/fabric-loom/1.21.7/` (regenerated with `./gradlew :common:genSources`, extracted to
`.gradle/loom-cache/minecraftMaven/...-sources.jar`), never from the version number.

| Area | 1.21.7 | evidence | what this port does |
|---|---|---|---|
| `ResourceLocation` vs `Identifier` | **`ResourceLocation`** | `net.minecraft.resources.Identifier` does not exist; `ResourceLocation` is there | 30 files renamed back |
| Render types | **hybrid**: `RenderStateShard` + `RenderType.CompositeState` + `RenderPipeline`, but `RenderStateShard#CULL`/`#NO_CULL`/`RENDERTYPE_LEASH_SHADER` are **gone** and `RenderType#create` only takes a `RenderPipeline` | `javap RenderType`: the only `create` overloads are `(String, int, RenderPipeline, CompositeState)` and the private 6-arg one; `javap RenderStateShard` has no cull shards | `client/renderer/RenderTypes.java` builds its own `RenderPipeline` from `RenderPipelines#MATRICES_FOG_SNIPPET`, registers it in `RenderPipelines#PIPELINES_BY_LOCATION` and wraps it with `RenderType.CompositeState.builder().setTextureState(NO_TEXTURE).setLightmapState(LIGHTMAP)` — 5 new AW + 5 new AT entries |
| Entity rendering | **hybrid**: `EntityRenderer<T, S extends EntityRenderState>` with `extractRenderState` + `render(S, PoseStack, MultiBufferSource, int)`; **no** `SubmitNodeCollector`, **no** `CameraRenderState` | `javap EntityRenderer` | `ZiplineRopeRenderer` is the render-state shape from 1.21.11 with `submit(…)` replaced by `render(…)` and the buffer written directly |
| Player rendering | **old side**: `net.minecraft.client.model.PlayerModel` (no type parameter), `PlayerRenderer`, `PlayerRenderState`; **but** the model is still a *tree* (`leftSleeve = leftArm.getChild("left_sleeve")`) and `setupAnim`/`setupRotations` already take only the render state | `javap PlayerModel`'s constructor: `getChild(...)` for every second-layer part | `PlayerModelTransformer` keeps the 1.21.11 form (attack time + arm poses come from the state, second layer is reset not copied); `PlayerModelMixin` / `PlayerRendererMixin` are the 1.21.11 form retargeted at `PlayerRenderState`, with `compat/IPlayerRenderStateEntity` + `PlayerRenderStateEntityMixin` + `PlayerRenderStateExtractorMixin` in place of the `Avatar*` trio (`AvatarRenderer`/`AvatarRenderState`/`Avatar` do not exist in 1.21.7) |
| Key mappings | **old side**: category is a `String`, `KeyMapping.MAP` is `Map<Key, KeyMapping>` (one mapping per physical key) | `javap KeyMapping` | 1.21.1's `KeyBindings` verbatim, including `restoreVanillaBindings()` and the `KeyRecorder#onClientTick` call that drives it, and the `isPollableKeysym` guard that stops the GLFW `Invalid key -1` spam. Lang key is `key.categories.parcool` again |
| Entity / block entity save | **new side**: `ValueInput`/`ValueOutput` | `javap Entity` / `javap BlockEntity` | 1.21.11's forms |
| `Entity#hurt` | **new side**: `public final void hurt(DamageSource, float)` + `hurtOrSimulate` + `abstract hurtServer` | `javap Entity` | `ZiplineRopeEntity#hurtServer` returns false; `LivingEntityFallMixin` redirects to the `void hurt` call |
| `jumpFromGround` | **new side**: on `LivingEntity` | `javap LivingEntity` / `javap Player` | `mixin.common.LivingEntityJumpMixin` kept |
| `Entity#isInWaterOrBubble` | **removed** | not in `javap Entity` (only `isInWater`, `isInWaterOrRain`, `isUnderWater`, `isEyeInFluid`) | `utilities/EntityUtil#isInWaterOrBubble` (1.21.11 form) |
| `Player#canInteractWithEntity` | **still present** | `javap Player` | `PlayerVisibilityHandler` documents why `getVisibilityPercent` is the better hook and 1.21.11's mixin is kept |
| `BlockEntityType.Builder` | **deleted**, and there is **no static `register` either** — only the private 2-arg constructor | `javap BlockEntityType` (5 methods, one private ctor) | **differs from the 1.21.11 tree**, which invokered a `register(String, factory, Block...)` that no longer exists. Here the type is constructed directly (`new BlockEntityType<>(factory, Set.of(blocks))`) with the constructor + the nested `BlockEntitySupplier` widened through AW/AT, inside a plain Architectury `DeferredRegister` — so `ParCoolPlatform#registerBlockEntityType`, `BlockEntityTypeInvoker` and the `setModEventBus` platform seam are all gone (see §4) |
| `Registry#get` | **new side**: `Optional<Holder.Reference<T>>` | `javap Registry` | 1.21.11's forms |
| `Item.Properties` | **new side**: `setId(ResourceKey<Item>)` is mandatory (`Objects.requireNonNull(this.id, "Item id not set")`), `noCollision()` is gone (only the vanilla typo `noCollission()`), `useBlockDescriptionPrefix()` is what replaces the deleted `BlockItem#getDescriptionId` | `javap Item$Properties`, `javap -c Item$Properties.effectiveDescriptionId` | `Items` keeps `setId` and gains `useBlockDescriptionPrefix()`; `Blocks` uses `setId` and `noCollission()`. See §4 |
| `InteractionResult` | **interface** | `javap` | 1.21.11's `SUCCESS`/`SUCCESS_SERVER` spelling |
| `Input` / `ClientInput` | **record**, but **no `keyPresses` inside `Input`** — `Input` is `forward/backward/left/right/jump/shift/sprint`; `ClientInput.keyPresses` is the `Input` and `forwardImpulse`/`leftImpulse`/`jumping` are gone | `javap Input`, `javap ClientInput` | `KeyBindings` uses `input.keyPresses.forward()` etc. **This is the one place where the hand-over note for the other ports is wrong** — the `keyPresses` *field* arrived with 1.21.2 but the *field inside `Input`* did not, so the 1.21.11 shape of the accessors happens to be right anyway |
| `ItemTintSources` | **exists** (1.21.5 rewrite) and `net.minecraft.client.color.item.ItemColor` does **not** | `javap`, decompiled `ItemTintSources` | 1.21.11's `ItemColors` + `ItemTintSourcesAccessor`. Architectury 18.0.8 has no `ColorHandlerRegistry.registerItemColors` either (checked with `javap` on the jar), so the codec registry is the only route. The single tint entry really does reproduce 1.21.1's `i > 0 ? -1 : color`: `ItemRenderer#getLayerColorSafe` returns `-1` for an out-of-range quad tint index |
| Recipes | **1.21.5 ingredient form is *not* in effect**: vanilla 1.21.7 recipes use plain strings and `"#tag"`, results use `"id"` | `unzip -p minecraft-client.jar data/minecraft/recipe/{chain,oak_door,diamond_sword}.json` | 1.21.11's recipe JSONs, except `minecraft:chain` (1.21.11's `minecraft:iron_chain` is a 1.21.9+ item) |
| `pack.mcmeta` | `PackMetadataSection` has only `description`, `pack_format` and a **lenient** `supported_formats` — **`min_format` / `max_format` do not exist** and are silently dropped | decompiled `PackMetadataSection`, `Pack#getDeclaredPackVersions` | `pack_format: 81` + `supported_formats: [64, 81]`, no min/max. `81` = `SharedConstants.DATA_PACK_FORMAT` and `64` = `RESOURCE_PACK_FORMAT` for 1.21.7; the range has to contain the declared main format or the game falls back to `InclusiveRange(81)` and warns on the client |
| Item models | `assets/<ns>/items/<id>.json` (`ClientItemInfoLoader`, `LISTER = FileToIdConverter.json("items")`); `assets/<ns>/models/item/**` is **dead** | decompiled `ClientItemInfoLoader` | `assets/parcool/items/*.json` kept, `assets/parcool/models/item/*.json` deleted |
| `Level` ctor | no `ProfilerFiller` argument | `javap Level` | 1.21.11's `ClientWorldMixin` |
| `GameRenderer#renderLevel` | takes `DeltaTracker` | `javap` | 1.21.11's explicit descriptor |
| `Camera` | **old side**: `getXRot()`/`getYRot()`, `getUpVector()`/`getLeftVector()`, `setup(BlockGetter, Entity, boolean, boolean, float)` | `javap Camera` | 1.21.1's `CameraAnglesMixin` |
| `KeyMapping#key` | still `private` | `javap` | 1.21.1's `KeyMappingMixin` (a shadow may not widen, so 1.21.11's `protected` would not compile) |
| `LocalPlayer#move` | **overridden** (1.21.11's comment) | `javap LocalPlayer` | 1.21.11's `LocalPlayerMixin`: it does not extend `AbstractClientPlayer` and applies the enforced move with `((Entity) player).move(…)` |
| `Player#causeExtraKnockback` | **does not exist**; `setSprinting(false)` is still inline in `Player#attack` | `javap Player`; `javap -c Player.attack` finds `invokevirtual setSprinting:(Z)V` | `PlayerMixin`'s `@WrapWithCondition` targets `attack`. Loom's remapper printed `Cannot remap causeExtraKnockback because it does not exist in any of the targets` while the 1.21.11 version was in place — that warning is the cheap way to catch this class of mistake |
| `ArgumentTypeInfos#register` | still `private static` | `javap` | 1.21.1's mixin + the AW entry (needed on both loaders) |
| `LivingEntityRenderer#shouldShowName` | `protected boolean shouldShowName(T, double)` — 1.21.1's shape | `javap` | 1.21.1's mixin, without the `EntityRenderer`/`RenderLayerParent` supertypes (1.21.2 added a second type parameter) |
| `Commands.LEVEL_GAMEMASTERS` | an **`int`**, not a predicate; `CommandSourceStack#permissions()` is gone, `hasPermission(int)` is the API | `javap Commands`, `javap CommandSourceStack` | `commandSource.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| `CustomRecipe#canCraftInDimensions` | **gone** | `javap CustomRecipe` | 1.21.11's `ZiplineRopeDyeRecipe` |
| `Item#appendHoverText` | `(stack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` | `javap Item` | 1.21.11's `ZiplineRopeItem` |
| `BlockBehaviour#onRemove` | **gone** | `javap BlockBehaviour` | `ZiplineHookTileEntity#preRemoveSideEffects`, called from `LevelChunk#setBlockState` (server only, before the block entity is dropped) — 1.21.11's shape |
| `Potion` | `new Potion(String name, MobEffectInstance…)`; the `name` is what `PotionContents#getName` turns into `item.minecraft.potion.effect.<name>` | decompiled `Potion`, `PotionContents` | 1.21.11's `Potions` passes `"energy_drink"` / `"poor_energy_drink"`, which is exactly what the lang files already contain. Without it the potions would be unnameable |
| Sounds | asset index 26 still has `entity/leashknot/leash1..3.ogg` (the 1.21.1 `place1..3` are gone) | `~/.gradle/caches/fabric-loom/assets/indexes/1.21.8-26.json` | 1.21.11's `sounds.json` kept |
| NeoForge mapping naming | mojmap | 1.21.11's `neoforge.mods.toml` / Paraglider 21.5 bytecode references `net.minecraft.resources.ResourceLocation` | mojmap `accesstransformer.cfg` |

---

### PROMPT.md corrections made from this port

`PROMPT.md` shipped with this tree, but several of its claims are wrong for 1.21.7 and a handoff
document with wrong lines is worse than no lines, so they were corrected in place (section 6a, plus
the `--stop` / run-phase / DoD notes):

* the render-type, entity-rendering and player-rendering rows describe 1.21.11 as if it were a single
  side; 1.21.7 is a three-way hybrid on all three;
* the recipe row has the two forms swapped for this version;
* the `pack.mcmeta` row promises `min_format`/`max_format`, which 1.21.7's codec does not have;
* the `BlockEntityType` row points at a static `register` that 1.21.7 does not have;
* neither the `Item.Properties#setId` / `BlockBehaviour.Properties#setId` requirement nor
  `useBlockDescriptionPrefix` is mentioned anywhere, and the mod does not initialise without them;
* phase 8 told the reader to run `./gradlew --stop`, which this machine's instructions forbid;
* the definition of done ticked two runtime boxes that this tree cannot tick.

## 3. Mixin set

Final set (27 mixins, `injectors.defaultRequire: 1`):

* **Dropped from 1.21.11**: `AvatarRenderStateEntityMixin`, `AvatarRenderStateExtractorMixin`
  (retargeted to `PlayerRenderStateEntityMixin` / `PlayerRenderStateExtractorMixin`),
  `BlockEntityTypeInvoker` (no `BlockEntityType#register` to invoke any more — the constructor is
  widened instead).
* **Dropped from 1.21.1**: `client.ClientPacketListenerMixin` (it exists only in that tree; 1.21.11
  dropped it and nothing in 1.21.7 needs it back).
* **Added**: `common.LivingEntityJumpMixin` (moved up from `PlayerMixin`).
* **Reverted to 1.21.1**: `client.CameraAnglesMixin`, `client.KeyMappingMixin`,
  `client.LivingRendererMixin`.

Every `@Inject`/`@Redirect`/`@WrapWithCondition`/`@Shadow`/`@Accessor` target was checked with `javap`
against the mojmap 1.21.7 jar, and the two `@At("INVOKE", target = …)` redirects were checked against
the *bytecode*:

* `LivingEntityFallMixin` → `LivingEntity#causeFallDamage` really contains
  `invokevirtual hurt:(Lnet/minecraft/world/damagesource/DamageSource;F)V`.
* `PlayerMixin` → `Player#attack` really contains `invokevirtual setSprinting:(Z)V`.

**This port was never booted** (see §6), so "the target exists" is as far as the mixin verification
went. A `defaultRequire: 1` wrong target is a hard boot failure, and a right-name/wrong-overload
injection is a silent no-op; neither was exercised at runtime.

---

## 4. Bugs found upstream, and what this port did

### Taken from the 1.21.1 tree (the 1.21.11 tree is a regression on both)

* **`ConfigSpec#persist()` writes nothing in the 1.21.11 tree.** `ConfigValue#save()` calls
  `persist()`, which only sets a `dirty` flag that nothing ever reads, so every setting made in the
  in-game screens is lost. The 1.21.1 version calls `save(file)` and the file really is rewritten.
  Copied. (The 1.21.11 version was an attempt to debounce the per-frame writes a slider drag produces;
  the real fix is a debounced writer, which is a redesign, not a port. Left for the next person.)
* **`utilities.BufferUtil` regressed in 1.21.11.** `ensureRoom` was made `static` over a `static
  ByteBuffer current` that is only ever assigned by the private constructor — a data race — and the
  overflow checks were *deleted* from `putVector3i`/`putVec3` altogether. The 1.21.1 version is
  instance-based, checks every write, and names the payload that overflows. Copied.

### Not inherited, because this port does not have the structure

* **`RenderTypes` is only registered on the first frame in the 1.21.11 tree.** Its static initialiser
  is what puts the two pipelines into `RenderPipelines.PIPELINES_BY_LOCATION`, and
  `ShaderManager#apply` snapshots and *precompiles* that map during the resource reload and throws on
  the first pipeline that fails. Nothing touches `RenderTypes` before the first rope is drawn, i.e.
  long after the reload. `Renderers.register()` now calls `RenderTypes.register()` explicitly, so the
  pipelines are in place during mod init. **The 1.21.11 tree has this bug and the 1.21.1 tree does not
  (it has no pipelines).** A one-line fix worth carrying back.
* **No `BlockEntityType` platform seam here.** The 1.21.11 tree needed `ParCoolPlatform#registerBlockEntityType`
  because `BlockEntityType.Builder` was gone and only a private `register` was left; Architectury's
  `DeferredRegister` (which the 1.21.1 tree used, and which works for every other registry here) puts
  the value into the registry inside the writable window anyway, so on 1.21.7 the plain
  `DeferredRegister` is enough once the constructor is widened. The seam, the `BlockEntityTypeInvoker`
  and `NeoForgeParCoolPlatform#setModEventBus` were deleted rather than adapted.

### Fixed / added in this port

* `Item.Properties#useBlockDescriptionPrefix()` on the two hook items. Without it every hook is
  `item.parcool.*` and the `block.parcool.*` keys in the eleven language files go unused. The 1.21.11
  tree "solved" this by *adding* `item.parcool.wooden_zipline_hook` / `item.parcool.iron_zipline_hook`
  to every language file, which papers over the regression instead of fixing the cause; those two
  additions were removed here and the `key.category.parcool` → `key.categories.parcool` rename undone.
  **The 1.21.11 tree's language files are wrong for 1.21.11-adjacent versions that still use a String
  key category.**
* `RenderStateShard.NO_TEXTURE` / `LIGHTMAP` are `protected` in 1.21.7 and `RenderType#create` /
  `RenderPipelines.{PIPELINES_BY_LOCATION, MATRICES_FOG_SNIPPET}` are package-private/private. All five
  are widened for both loaders. The 1.21.1 tree's `accesstransformer.cfg` widens the *same kind* of
  members but writes them with SRG names (`f_110147_` for `RENDERTYPE_LEASH_SHADER`), which never match
  a mojmap-named runtime — the entries are silently ineffective.
* `PackMetadataSection` in 1.21.7 has no `min_format`/`max_format`; the 1.21.11 `pack.mcmeta` carries
  them and they are dropped on the floor.
* `assets/parcool/models/item/*.json` are dead on 1.21.7 (item definitions moved to
  `assets/<ns>/items/`) and were deleted.
* `ParCoolPlayerStates` / `ParCoolPlugin` compiled against the **1.21.11** Paraglider build failed
  outright, because that build's own bytecode references `net.minecraft.resources.Identifier`, which
  does not exist in 1.21.7. Paraglider publishes no 1.21.6/1.21.7 build; the integration is now
  compiled against the 1.21.5 build (`6739612`), the newest one still on the `ResourceLocation` side of
  that API, and it stays `optional` at runtime.

### Found, not fixed (upstream behaviour, out of scope for a port)

* `Limitations` filename validation: **present and identical in both reference trees** — PROMPT.md's
  claim that the 1.21.11 tree removed it is wrong. Not touched.
* 26 unused imports remain. They are the same set that both reference trees carry (27 in 1.21.11, 28 in
  1.21.1); the six this port introduced while re-porting files were removed. Do not sweep the rest.
* The deprecated-for-removal `NetworkManager.registerReceiver(Side, ResourceLocation, …)` /
  `sendToServer(ResourceLocation, …)` overloads in `NeoForgeParCoolNetwork`. The `CustomPacketPayload.Type`
  overloads are the ones Architectury will keep, but mixing the two families breaks the
  `NetworkAggregator.C2S_TYPE`/`S2C_TYPE` maps and the first client→server packet is dropped
  ("Network Protocol Error"). Switching means one id per direction *and* the type-based send, which is a
  redesign.

---

## 5. Verification actually performed

* `./gradlew :common:build && ./gradlew build` from a deleted `build/`, `*/build`, `.gradle` and
  `.architectury-transformer`: **BUILD SUCCESSFUL**, both artifacts produced.
* `:common:checkCommonLoaderIndependence`: passed (it runs as part of `:common:check`).
* Fabric jar: 349 classes = all 345 `:common` classes + the 4 `fabric` module classes; mixin config has
  27 entries and all 27 classes are present; `parcool.accesswidener` is `v2 intermediary`; **no
  refmap**; mixin targets are remapped into the bytecode (`method_7324` for
  `Player#attack`, `method_6043` for `LivingEntity#jumpFromGround`, …).
* NeoForge jar: 360 classes = all 345 `:common` + all 14 `neoforge` classes + the exploded common
  output; `META-INF/accesstransformer.cfg`, `neoforge.mods.toml`, the `ServiceLoader` file,
  `parcool-common.mixins.json`, `pack.mcmeta`, `LICENSE` and every resource are present; mixin targets
  stay mojmap.
* **Every AW entry verified applied**: each member is `public` in the AW-applied
  `minecraft-merged-…jar` under `.gradle/loom-cache/minecraftMaven/`.
* **Every AT entry verified applied**: each member is `public` in
  `neoforge/build/moddev/artifacts/neoforge-21.7.25-beta.jar`, which is the AT-transformed class set
  the dev runtime and the production runtime both load.
* No `System.out`, no `printStackTrace`, no `TODO`/`FIXME`, no absolute path in any checked-in file
  (the only hits are in generated `build/` output, which is git-ignored).
* Metadata: `fabric.mod.json` → `minecraft: ~1.21.7`, `architectury: >=18.0.0`;
  `neoforge.mods.toml` → MC `[1.21.7,1.22)`, NF `[21.7.0-beta,)`, architectury `[18.0.0,)`,
  paraglider `[21.5.0,)`, shouldersurfing `[4.11.0,)` (its actual mod version), betterthirdperson
  `[1.9.0,)`.

## 6. What was **not** verified

The brief for this port forbade launching Minecraft, so the entire runtime half of the acceptance list
is open. None of the following was executed:

* the game booting on either loader, on a dev run or in Prism;
* `Player#createAttributes` resolving the two attributes (the step that dies with
  `Registry is already frozen` if the NeoForge attribute split is wrong for 21.7);
* `grep "GL ERROR"` and `grep "Invalid key"` over a real log;
* every `key.parcool.*` binding being rebindable, and vanilla right-click / Space / Ctrl still working
  alongside ParCool (this is the `restoreVanillaBindings` check, and 1.21.7 is on the
  one-mapping-per-key side, so it is the one most likely to matter);
* one action of each family, the settings screen, the stamina HUD, the zipline render;
* two clients on one server, i.e. the `ActionStatePayload` broadcast path;
* the zipline rope's actual pixels — the pipeline registration order (`RenderTypes.register()` from
  `Renderers.register()`) is reasoned from `ShaderManager#apply`, not observed.

## 7. What the next port must not trust

1. **`minecraft-merged-*-sources.jar` lies about visibility.** It is decompiled from the
   AW-applied jar. Use `javap` on the class jar for every access question.
2. **A wrong descriptor in `parcool.accesswidener` is silently ignored.** `Player#canPlayerFit…` and
   `Pose`'s package are the two that bit here. Check `javap` on the AW-applied jar
   (`.gradle/loom-cache/minecraftMaven/**`), not on the plain Mojang jar.
3. **The AW is not part of the Loom artifact cache key.** After editing it, delete
   `.gradle/loom-cache` and `*/build`, or the previous (un-widened) jar is reused.
4. **A one-name `@Inject` picks the wrong overload** whenever the target has a bridge method.
   `PlayerModel` has `setupAnim(PlayerRenderState)` plus two bridges, `PlayerRenderer` has
   `setupRotations(PlayerRenderState, …)` plus the `LivingEntityRenderState` one, and
   `LivingEntityRenderer` has two `shouldShowName`. All three mixins use an explicit descriptor here.
5. **`$` in AW field/entry names.** `BlockEntityType$BlockEntitySupplier` is correct for both the AW
   and the AT; the AT additionally needs the explicit return type on `<init>`.
6. **Vanilla's own recipe files are the ground truth for the recipe format**, not the version number
   and not the neighbouring port. 1.21.5 did *not* make 1.21.7's shaped-recipe `key` an object.
7. **The version number does not tell you which side of a seam you are on** — 1.21.7 is a genuine
   hybrid (`RenderStateShard` render types but render-state entity renderers and render-state player
   model hooks; `ItemTintSources` but no `ItemColor`; `ValueInput` but `ResourceLocation`), and so are
   1.21.2–1.21.5. `javap` it.
8. **The `parcool.accesswidener` is a `v2 named` file that Fabric Loader only accepts as
   `v2 intermediary` in the distributed jar.** `:common:remapJar` copies it verbatim, which is why
   `:fabric:distJar` drops the common copy and takes the fabric module's.
9. **The one-shot `./gradlew build` on a clean checkout still does not work** (see §1). Run
   `:common:build` first; do not "fix" it.

## Recipe `category` field (added by the orchestrator, after the port was built)

`"category": "misc"` was added to all five `data/parcool/recipe/*.json`.

**Correction — an earlier version of this note claimed the field was mandatory and that its
absence broke every recipe. That was wrong, and the claim is retracted here.** The bytecode
actually reads

```
CraftingBookCategory.CODEC
  .fieldOf("category")                       // offset 26
  .orElse(CraftingBookCategory.MISC)        // offset 34
```

`MapCodec#orElse` supplies `MISC` when the key is absent, so a recipe without `category`
parses fine. Verified the same way on the 1.21.2 and 1.21.7 mojmap jars; agents on 1.21.9 and
1.21.10 independently reached the same conclusion and had it disproved by decoding the real
codec. Adding the field explicitly is harmless (it is exactly the codec's own default) and
matches what upstream ParCool does on newer versions, so it stays.

Two related claims from the same handoff were also wrong and are recorded so nobody re-chases
them:

- `minecraft:iron_chain` is **not** a 1.21.11-only item. It exists in 1.21.9 and 1.21.10 --
  verified in `data/minecraft/recipe/iron_chain.json`, `assets/minecraft/items/iron_chain.json`
  and the textures inside the client resources jar. The vanilla rename is `chain` ->
  `iron_chain` and it landed before 1.21.9. The correct spelling for this port is therefore
  whatever the target's own resources jar contains; for 1.21.2 through 1.21.8 that is
  `minecraft:chain`.
- The object form `{"item": ...}` for recipe ingredients does **not** parse on any of these
  versions: `Ingredient.CODEC` is a holder-set codec whose string branch is what
  `"minecraft:chain"` goes through. The string form is the correct one throughout.

## The network decode ran on a released buffer (found by launching the game, 1.21.7)

**1.21.7 is the first port in this series whose client actually launched and joined a world.** The
three waves before it all passed acceptance on "the build is green", and the bug below was sitting
in the tree the whole time. Nothing about it is visible to a compiler, to `javac` warnings, to
`checkCommonLoaderIndependence`, to the mixin-target checker, or to any test that does not put a
real client on a real server. It only fires on the first ParCool packet that actually crosses the
wire, i.e. only after a world is loaded and packets flow.

### The stack that found it

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

It repeats every few seconds. The user described 1.21.7 as "almost nothing worked", and that is
literally what happened: the server task dies on every incoming ParCool packet, the limitation
snapshot never arrives, and no action can start.

### Why it happened

One line, identical in `FabricParCoolNetwork` and `NeoForgeParCoolNetwork`:

```java
(buf, context) -> context.queue(() -> handler.accept(erased.decode((RegistryFriendlyByteBuf) buf), context))
```

`NetworkManager.registerReceiver` hands the receiver a **raw** `RegistryFriendlyByteBuf` and releases
it as soon as that lambda returns. `context.queue(...)` moves the work to the main thread, i.e. to
*after* the release. The decode therefore reads memory that netty has already freed, and the first
`VarLong.read()` in any ParCool payload codec throws `IllegalReferenceCountException: refCnt: 0`.

The queue is needed for **thread safety of the handler** — the handler touches the player, the level
and the mod's own state, none of which may be touched from a netty thread. The queue is **not**
needed for decoding. Decoding is pure byte-reading, it is safe on the netty thread, and it is only
safe on the netty thread.

The correct order is decode first, then queue only the handler:

```java
(buf, context) -> {
    T payload = erased.decode((RegistryFriendlyByteBuf) buf);
    context.queue(() -> handler.accept(payload, context));
}
```

`NetworkChannel`, which the 1.21.3 Fabric path uses, has always had this shape — the channel decodes
in its own decoder lambda and passes an already-built payload to the receiver, so
`context.get().queue(() -> handler.accept(payload, …))` only ever queues the handler. Porting 1.21.3
onto the raw id-based `NetworkManager` API in 1.21.5/1.21.6/1.21.7 collapsed those two lambdas into
one and moved the decode into the queue with them. That collapse is the entire regression.

The comment in the source is mandatory and must not be deleted or shortened: without it the next
reader sees a decode that "could just as well" happen inside the `queue`, concludes the rewrite is
equivalent, and puts the bug straight back.

### This class of defect is invisible to everything except a live client

Recorded separately on purpose, because it is the general lesson and not just this port's story:

* `javac` sees `StreamCodec.decode(ByteBuf)` return `T` and `PacketContext.queue(Runnable)` return
  `void`. Both signatures are correct. The bug lives entirely in *when* the call happens relative to
  a release that happens outside the type system, in a lambda Architectury invokes.
* `IllegalReferenceCountException` is a runtime netty invariant, not a type error. There is no
  annotation, no null check and no assertion that would flag it.
* Nothing in the build exercises it. `:common:checkCommonLoaderIndependence` is a source-level
  loader-leak check; the mixin checker is a static target check; there is no client, no packet
  capture, no integration test. Green build, green acceptance, dead mod.
* **The packets have to actually flow.** Without a client entering a world there is no inbound
  ParCool packet, so the lambda never runs and the bug cannot manifest. A dedicated server started
  with no client connected proves nothing here either — the NeoForge `C2S_TYPE` NPE in §4 of this
  file was invisible for exactly the same reason.
* The only detector is a real client on a real server, and then reading the log. `refCnt: 0` in a
  stack trace is the signature.

## The Architectury toolchain change (1.21.7, `18.0.8` → `17.0.8`)

Found while chasing the above: the client was not even reaching the packet handler, because Fabric
Loader aborted the boot. `architectury_api_version` is now `17.0.8`, and the mod's own declared
lower bounds moved with it (`fabric.mod.json` `"architectury": ">=17.0.8"`,
`neoforge.mods.toml` `versionRange = "[17.0.8,)"`).

Independently re-verified for this pass, from the maven metadata rather than from the handoff:

| architectury-fabric | fabric-api it drags | fabric-loader it drags | vs. this port's pins | result |
|---|---|---|---|---|
| 18.0.8 | 0.136.0+1.21.10 | 0.17.2 | port pins 0.128.2+1.21.7 / 0.16.14 | port **loses**, broken |
| 17.0.8 | 0.128.1+1.21.7 | 0.16.14 | port pins 0.128.2+1.21.7 / 0.16.14 | port **wins**, ok |

`architectury-fabric` declares `fabric-api` as a hard runtime dependency, so Gradle's "highest wins"
silently replaces the port's own pin. `fabric-api` builds are MC-pinned —
`0.128.2+1.21.7` declares `minecraft >=1.21.7- <1.21.8-` (read out of its own `fabric.mod.json`),
while `0.136.0+1.21.10` demands `>=1.21.10- <1.21.11-` and Fabric Loader refuses the boot:

```
[FabricLoader/Resolution] Immediate reason: [HARD_DEP architectury 18.0.8
  {depends fabric-api @ [>=0.127.0]},
  HARD_DEP_INCOMPATIBLE_PRESELECTED fabric-api 0.136.0+1.21.10
  {depends minecraft @ [>=1.21.10- <1.21.11-)}, ROOT_FORCELOAD_SINGLE architectury 18.0.8]
[FabricLoader/ERROR] Incompatible mods found!
```

`architectury-neoforge:17.0.8` exists and is compatible: it declares `minecraft [1.21.4,)` and
`neoforge [21.0.110-beta,)`, which accepts this port's `21.7.25-beta`. API compatibility was
checked rather than assumed — every `dev.architectury.*` class ParCool references
(`NetworkManager`, `EventFactory`, `ClientGuiEvent`, `ClientPlayerEvent`, `ClientTickEvent`,
`CommandRegistrationEvent`, `EntityEvent`, `LifecycleEvent`, `PlayerEvent`, `TickEvent`, `Platform`,
`KeyMappingRegistry`, `EntityRendererRegistry`, `DeferredRegister`, `RegistrySupplier`, `Env`,
`Event`, `EventResult`) is present in both 17.0.8 and 18.0.8, and `NetworkManager$PacketContext` is
byte-identical between the two (`getPlayer`, `queue`, `getEnvironment`, `registryAccess`, `getEnv`).
The downgrade is a strict superset for this port.

For contrast, 1.21.5 and 1.21.6 were checked for the same trap and are **not** affected:
`architectury-fabric 16.1.4` drags `fabric-api 0.119.5+1.21.5` and `architectury-fabric 17.0.6` drags
`fabric-api 0.127.0+1.21.6`, both *below* the `0.128.2` those ports pin, so the port's own version
wins and the classpath stays on the right Minecraft. Only 1.21.7 needed the downgrade.

## Re-published artifacts (after the decode fix and the Architectury downgrade)

```
0.1-mc1.21.7fabric-3.4.3.3.jar     sha256 01209d11bb0967a26202b0348e53707c0f081de7cafb278df104939d81030ca5
0.1-mc1.21.7neoforge-3.4.3.3.jar   sha256 5019aa85551f3bb8d909cb06e4fdc6efb89f58ce270609f33799f85cbfbc7667
```

copied to `/home/sanufsoii/ports/готовые порты/parcool/`. Both differ from the previously published
pair (`8ee8516d22dc87088078904aabf2226452b30b29bcd0324dd4d12985e60d8350` /
`5b328a5b765196449f24569617375683b774fa4c7f4b190cc933afc981f2676e`) — as they must, since the network
code is in the jar. **The game was not launched after either change.** This artifact is "compiles and
contains the fixed bytecode", which is not "works": the 1.21.7 client has to be launched again and
have actually joined a world before the `refCnt: 0` stack trace can be called gone.
