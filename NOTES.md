# NOTES.md — ParCool, Architectury API port for Minecraft 1.21.3

Everything here was established while porting; nothing was taken on trust from the other branches.
Where the handoff document (`PROMPT.md`) was wrong for 1.21.3, the correction is written down here and
the file itself was fixed.

---

## 1. Resolved toolchain, and where each number came from

| Setting | Value | Source |
|---|---|---|
| `minecraft_version` | `1.21.3` | target of the task; confirmed by `version.json` inside `minecraft-client.jar` (`id: 1.21.3`, `protocol_version: 768`, `pack_version: {resource: 42, data: 57}`) |
| `neo_version` | `21.3.97` | last entry of the `21.3.*` block in `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` |
| `loader_version` | `0.16.10` | `https://meta.fabricmc.net/v2/versions/loader/1.21.3` (0.19.x exists but is the 1.21.9+ line; 0.16.x is the 1.21.1–1.21.4 line) |
| `fabric_api_version` | `0.114.1+1.21.3` | `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`, filtered to `+1.21.3` |
| `architectury_api_version` | `14.0.4` | highest `14.x` in `architectury-fabric/maven-metadata.xml`; each candidate jar's `fabric.mod.json` was read: 13.x declares `minecraft: ~1.21-`, **14.x declares `~1.21.2-`**, 15.x starts at `~1.21.4-` |
| `dev.architectury.loom` | `1.7.435` | highest `1.7.x` in `dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml`; 1.7.x is the line that still configures a 1.21.3 Minecraft |
| `net.neoforged.moddev` | `1.0.24` | last `1.0.x` directory under `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/` |
| `architectury-plugin` | `3.5.170` | unchanged from both reference ports (latest in `architectury-plugin.gradle.plugin/maven-metadata.xml`) |
| Gradle wrapper | `8.10.2` | the wrapper the 1.21.1 port uses; ModDevGradle 1.0.x and Loom 1.7.x are both Gradle 8 line |
| Java toolchain | `21` | `java { toolchain { languageVersion = 21 } }` in the root `build.gradle`; the machine's default JDK is 25, `~/.gradle/gradle.properties` pins the Gradle JVM to `/usr/lib/jvm/java-21-openjdk` |
| `org.gradle.jvmargs` | `-Xmx2G` | lowered from the references' `-Xmx3G` because three ports build in parallel on this machine |

### Optional integrations, and the CurseForge file ids

CurseForge's own API needs a key and `api.curseforge.com` answers 403 without one, so the ids came
from `https://api.cfwidget.com/minecraft/mc-mods/<projectId>`, whose `files[]` entries carry the game
versions each file was published for. `cursemaven.com` still serves them:
`https://www.cursemaven.com/curse/maven/<slug>-<projectId>/<fileId>/<slug>-<projectId>-<fileId>.jar`.

| Mod | Project | File id | Build | Game versions |
|---|---|---|---|---|
| Paraglider | 289240 | `7795089` | `Paraglider-neoforge-21.1.5` | 1.21.1 |
| EpicFight | 405076 | `8175609` | `epic-fight-21.17.3.1-mc1.21.1-neoforge` | 1.21.1 |
| BetterThirdPerson | 435044 | `5859634` | `BetterThirdPerson-neoforge-1.9.0` | 1.21.2, **1.21.3** |
| ShoulderSurfing | 243190 | `5948017` | `ShoulderSurfing-NeoForge-1.21.3-4.6.3` | **1.21.3** |

**Paraglider and EpicFight have no 1.21.2/1.21.3 build at all.** Paraglider's file list jumps
1.21.1 (`21.1.x`) → 1.21.5 (`21.5.x`); EpicFight's newest release is still `21.17.3.1-mc1.21.1`. Their
APIs are unchanged across that gap and both integrations are `compileOnly` plus a
"is the class loadable" guard at runtime, so the 1.21.1 jar is the correct thing to compile against;
on a client that has no matching build, the integration reports itself absent and ParCool falls back
to its own stamina system. This is the same trade the 1.21.11 port made for EpicFight.

---

## 2. Corrections to PROMPT.md

`PROMPT.md` §6 lists the 1.21.1-vs-1.21.11 seams and says to work out the side per target. For
**1.21.3 the answer is neither of the two in most rows**, because 1.21.2 was a large internal rework
that 1.21.3 sits directly on top of. Verified against the decompiled mojmap sources in
`.gradle/loom-cache/minecraftMaven/…-sources.jar` and against
`neoforge/build/moddev/artifacts/neoforge-21.3.97-minecraft-sources.jar`.

| Area | 1.21.1 | 1.21.11 | **1.21.3 (verified)** |
|---|---|---|---|
| `ResourceLocation` / `Identifier` | `ResourceLocation` | `Identifier` | **`ResourceLocation`** (1.21.1 side) |
| `ValueInput`/`ValueOutput` save | `CompoundTag` | `ValueInput` | **`CompoundTag`** (1.21.1 side) |
| Render types | `RenderStateShard` | `RenderSetup`+`RenderPipeline` | **`RenderStateShard`** (1.21.1 side) |
| Entity rendering | `EntityRenderer#render(entity, …)` | `extractRenderState` + `submit` | **`EntityRenderer<T, S extends EntityRenderState>` with `extractRenderState` + `render(S, PoseStack, MultiBufferSource, int)`.** The render-state split landed in 1.21.2, but `SubmitNodeCollector` did **not** exist yet — `render` still takes the `MultiBufferSource` |
| `HumanoidModel` / `PlayerModel` | `HumanoidModel<T extends LivingEntity>`, `PlayerModel<T>` | `HumanoidModel<T extends HumanoidRenderState>`, `PlayerModel` in `…model.player` | **`HumanoidModel<T extends HumanoidRenderState>`, `PlayerModel` still in `net.minecraft.client.model`** (1.21.11 side) |
| Player renderer class | `PlayerRenderer` | `AvatarRenderer` | **`PlayerRenderer`** (1.21.1 side) |
| `BlockEntityType` construction | `BlockEntityType.Builder` | private ctor + `mixin.common.BlockEntityTypeInvoker` | **private ctor, no `Builder`** (1.21.11 side) |
| `jumpFromGround` | on `Player` | moved to `LivingEntity` | **moved to `LivingEntity`** (1.21.11 side) |
| `Entity#hurt` | `boolean hurt(DamageSource, float)` | `final void` + `hurtOrSimulate`/`hurtServer` | **`final void hurt` + `hurtServer`/`hurtClient`** (1.21.11 side) |
| `Item#descriptionId` | `BlockItem#getDescriptionId` delegates | field set at construction | **field set at construction** (1.21.11 side) |
| `InteractionResult` | `sidedSuccess(boolean)` | — | **`sidedSuccess` deleted**, use `SUCCESS` / `SUCCESS_SERVER` |
| `Block#useItemOn` | returns `ItemInteractionResult` | returns `InteractionResult` | **returns `InteractionResult`** (1.21.11 side) |
| `Block#updateShape` | 6-arg | `ScheduledTickAccess` 9-arg | **`ScheduledTickAccess` 9-arg** (1.21.11 side) |
| `EntityType.Builder#build` | `build(String)` | `build(ResourceKey)` | **`build(ResourceKey)`** (1.21.11 side) |
| `Registry#get` | value, `getHolder` key | `Optional<Reference<T>>` | **`Optional<Holder.Reference<T>>`** — the value accessor was renamed `getValue` (1.21.11 side) |
| Recipe ingredients | object, `{"item": "…"}` | string, `"minecraft:…"` | **string** (1.21.11 side). Verified in the 1.21.3 jar: `Ingredient.CODEC` is `HolderSetCodec(…, Item.CODEC, false)` and `Item.CODEC` is `holderByNameCodec()`, i.e. a bare string; the shipped `data/minecraft/recipe/oak_door.json` uses `"#": "minecraft:oak_planks"` |
| `GuiGraphics#blit` | no pipeline arg | `RenderPipelines.GUI_TEXTURED` first | **`Function<ResourceLocation, RenderType>` first** (`RenderType::guiTextured`) plus an explicit trailing tint |
| `Level` ctor | 9 args incl. `Supplier<ProfilerFiller>` | 8 args | **8 args** (1.21.11 side) |
| `Potion` ctor | no name | `Potion(String, MobEffectInstance…)` | **`Potion(String, MobEffectInstance…)`** (1.21.11 side) |
| `CustomRecipe` | `SimpleCraftingRecipeSerializer` | `CustomRecipe.Serializer` + `Holder<Potion>` mixes | **`CustomRecipe.Serializer`**, `canCraftInDimensions` gone, mixes take plain `Potion` |
| `FastColor` | `FastColor.ARGB32` | `ARGB` | **`ARGB`** (1.21.11 side) |
| `KeyMapping` category | `String`, `key.categories.*` | `Category` record, `key.category.*` | **`String`, `key.categories.*`** (1.21.1 side) |
| `KeyMapping.MAP` | one mapping per key | `Map<Key, List<KeyMapping>>` | **one mapping per key** (1.21.1 side) → `KeyBindings#restoreVanillaBindings` and `KeyRecorder#onClientTick`'s call are **kept** |
| `ClientInput` | loose boolean fields | `keyPresses` (`Input` record) + impulses | **`keyPresses` + impulses** (1.21.11 side) |
| `BlockBehaviour.Properties#id` | no such field | required, `setId` needed | **required, `setId` needed** (1.21.11 side) |
| `Item.Properties#id` | no such field | required, `setId` needed | **required, `setId` needed** (1.21.11 side) |
| `pack.mcmeta` | 34 | 81 + `min_format`/`max_format`/`supported_formats` | **42 + `supported_formats: [42, 57]`.** `PackMetadataSection` in 1.21.3 has only `description`, `pack_format` and an optional `supported_formats`; `min_format`/`max_format` do not exist, so the 1.21.11 file's extra fields are silently dropped by `lenientOptionalFieldOf` |
| `Entity#isInWaterOrBubble` | present | removed | **present** (1.21.1 side; no `EntityUtil.isInWaterOrBubble` needed) |
| `Player#canInteractWithEntity` | present | removed | **present** (1.21.1 side) |
| `LivingEntity#getVisibilityPercent` | absent | present | **present** — but the port uses `canInteractWithEntity`, the hook NeoForge's event actually fires from |
| Item model location | `models/item/*.json` | `items/*.json` | **`models/item/*.json`** (1.21.1 side; verified via `ModelDiscovery#registerStandardModels`, which rewrites the `item/` prefix) |
| NeoForge mapping naming | mojmap | mojmap | **mojmap**, re-verified for 21.3.97 (see §4) |

`PROMPT.md` §3 also said the phase-6 acceptance is "boot both loaders in a real Prism instance". That
was overridden for this port by an explicit instruction not to launch Minecraft; see §6.

---

## 3. Decisions taken file by file, and why

The tree was imported from the **1.21.11** port (structure, `build.gradle` files, packaging) and the
**1.21.1** port supplied the sources, because 1.21.3 is API-wise two versions from 1.21.1 and one from
1.21.11. Everything below is a case where a 1.21.1 source file had to move to the 1.21.11 side, or
where a 1.21.11 fix had to be carried back.

* **Taken from 1.21.11 verbatim** (renamed to `ResourceLocation` where needed):
  `ZiplineHookBlock` (the `ScheduledTickAccess` `updateShape` and the `InteractionResult` `useItemOn`),
  `TileEntities` + new `mixin/common/BlockEntityTypeInvoker` + `ParCoolPlatform#registerBlockEntityType`
  + `FabricParCoolPlatform`/`NeoForgeParCoolPlatform` implementations + `ParCoolNeoForge`'s
  `ModContainer` handoff (1.21.3 deleted `BlockEntityType.Builder` exactly like 1.21.11),
  new `mixin/common/LivingEntityJumpMixin` (1.21.3 moved `jumpFromGround` to `LivingEntity`; the two
  hooks were removed from `PlayerMixin`, where they would no longer match anything),
  `fabric/build.gradle` (the packaging fix: `distJar` from `:fabric:remapJar` first, the common access
  widener excluded, `version`/`group` declared so `fabric.mod.json` does not ship `"unspecified"`),
  `Action#wantsToShowStatusBar`/`getStatusValue` taking `Player` instead of `LocalPlayer`.
* **Rewritten for 1.21.3's half-way render state**: `ZiplineRopeRenderer`
  (`extractRenderState` + `render`, not `submit`), `PlayerModelMixin` and `PlayerRendererMixin` plus the
  new `compat/IPlayerRenderStateEntity`, `mixin/client/PlayerRenderStateEntityMixin` and
  `mixin/client/PlayerRenderStateExtractorMixin`, and `LivingRendererMixin` (three type parameters).
  `ZiplineRopeEntity#getCullingBoundingBox` is new for the same reason — 1.21.3 moved
  `getBoundingBoxForCulling` onto `EntityRenderer`.
* **`PlayerModelTransformer`** takes `attackTime` and the two arm poses as constructor arguments,
  because 1.21.3 moved `attackTime` onto `HumanoidRenderState` and no longer has
  `PlayerModel#leftArmPose` at all. `PlayerModelMixin` gets them from
  `PlayerRenderer.getArmPose(state, LEFT|RIGHT)` — the same public static the model's own
  `setupAnim` is about to call. `copyFromBodyToWear` / `reset` no longer `copyFrom` the parent onto
  the second skin layer: 1.21.3's `PlayerModel` already builds `left_sleeve` / `right_sleeve` /
  `left_pants` / `right_pants` / `jacket` as **children** of the limbs (see the `PlayerModel`
  constructor), so copying would apply the limb transform twice and put a second pair of arms next to
  the real ones.
* **`ShoulderSurfingDecoupledCamera` / `ShoulderSurfingManager`**: see §4.
* **`ClientPacketListenerMixin` kept** (it exists in the 1.21.1 tree, not the 1.21.11 one). Both
  targets, `ClientPacketListener#handlePlayerInfoRemove` and `#handlePlayerInfoUpdate`, still exist in
  1.21.3, so the mixin still applies. Its two handler bodies are empty loops in the 1.21.1 tree —
  dead upstream code, kept because removing it would be an unrelated behavioural change.
* **Not carried over from 1.21.11**: `compat/IAvatarRenderStateEntity`, `AvatarRenderState*Mixin`,
  `client/ItemTintSourcesAccessor`, `utilities/EntityUtil#isInWaterOrBubble`, `assets/parcool/items/`,
  `data/parcool/advancement/`, `Animation#setAnimator(Class, Object...)`. Each exists only because of
  a 1.21.11 API this target does not have. The advancements are a 1.21.11 addition with no
  counterpart upstream, so they are simply not part of this port.

---

## 4. Things that had to be re-derived rather than copied

**NeoForge's mapping naming (mojmap, re-verified for 21.3.97).** The 1.21.1 tree's
`accesstransformer.cfg` lists five `RenderStateShard` fields by SRG name (`f_110147_` = `NO_TEXTURE`,
`f_110158_` = `CULL`, …) and widens `net.minecraft.world.damagesource.DamageSources *()`. All five
fields are `public static final` in the NeoForge 21.3.97 patched sources, the class is never
referenced by the mod, and the AT must be mojmap — the `neoforge-21.3.97-sources.jar` MDG compiles
against is mojmap, and a shipped NeoForge mod's own AT
(`Paraglider-neoforge-21.1.5`, `META-INF/accesstransformer.cfg`) lists `aboveGroundTickCount`, not an
`f_110147_` token. An `f_…` entry never matches anything, so those six lines were dropped. The file
now has exactly one entry, `ArgumentTypeInfos#register`, which vanilla 1.21.3 *and* NeoForge 21.3.97
both keep private.

**The access widener.** The 1.21.1 tree widens `LivingEntity#swimAmount`, `#swimAmountO`,
`Entity#onGround`, `Player#canPlayerFitWithinBlocksAndEntitiesWhen`, `ArgumentTypeInfos#register` and
`LevelResource#<init>`. Only the `LevelResource` entry had to go: `public LevelResource(String)` is
already public in 1.21.3. The other five were verified as *still private/protected* — with a
`javap -p` on a `minecraftMaven` jar that Loom rebuilt after the widener was trimmed, because
removing a widener entry does not invalidate Loom's cache.

> **Trap worth writing down:** Loom's `minecraft-merged-*-sources.jar` in `.gradle/loom-cache` is
> generated *with the project's access widener applied*. Reading it tells you the post-widener access
> flags, not vanilla's. A first pass at this port concluded from it that five members were public,
> dropped the widener entries, and the next compile failed with four "has private access in Entity" /
> "has protected access in Player" errors. `rm -rf .gradle/loom-cache` and regenerate before trusting
> anything the sources jar says about access.

**`useLegacyMixinAp = false`.** Loom 1.7 defaults to the refmap flow, which cannot work for ParCool
(its mixin targets are class literals, so the generated refmap has no class-level entries and nothing
would read it — the jar would end up with mojmap `@Mixin` targets against an intermediary client).
The 1.21.11 tree dropped this setting because Loom 1.17 defaults to static remapping; this tree stays on
Loom 1.7 and so keeps the 1.21.1 tree's explicit setting. Verified in the built jar: no
`parcool-common-refmap.json`, not a single `net.minecraft.*` or `net/minecraft/world/...` string
anywhere in `com/alrex/parcool/**`, every Minecraft reference in the mixin classes is
`net/minecraft/class_XXXX`.

**`ShoulderSurfing` for 1.21.3 (file `5948017`, 4.6.3).** Upstream ParCool registers an
`ICameraCouplingCallback` and asks `IShoulderSurfing#isCameraDecoupled()`. Both arrived in
ShoulderSurfing **4.7.0**; 4.6.3, the newest build CurseForge published for 1.21.2/1.21.3, has
neither. The integration was re-expressed on the API 4.6.3 does have, and it is the same behaviour:

* `ShoulderSurfingDecoupledCamera` implements `ITargetCameraOffsetCallback`, and its `post` returns
  `Vec3.ZERO` while `ClingToCliff` is doing. `ShoulderSurfingCamera#calcOffset` runs every registered
  callback and stores `post`'s return value as the camera's `targetOffset`, which *is* the free-look
  offset that decouples the camera — zeroing it re-couples the camera, which is what
  `isForcingCameraCoupling` did.
* `isCameraDecoupled()` reads `IShoulderSurfingCamera#getTargetOffset().lengthSqr() > 0` instead of the
  missing `isCameraDecoupled()`.

**NeoForge attribute registration.** Both reference ports already split it:
`common/api/Attributes` *resolves* the holders on NeoForge and *writes* them on Fabric, and
`:neoforge`'s `NeoForgeAttributes` registers them through NeoForge's own `DeferredRegister` on the mod
event bus. 1.21.3's `Registry#get` already returns `Optional<Holder.Reference<T>>`, so the
1.21.11-shaped one-liner works unchanged. `RegisterEvent` is still the event name in 21.3.x.

---

## 5. Upstream and reference-port bugs found, and what was done about them

Found **in the 1.21.11 reference tree**, i.e. in code this port deliberately did *not* copy:

1. **`ConfigSpec#persist()` never writes — the settings screen stops persisting (1.21.11, serious).**
   `ConfigValue#save()` calls `owner.persist()`. In 1.21.11 `persist()` only sets
   `dirty = true`, `save(Path)` clears it, and `isDirty()` is never read anywhere in the tree
   (`grep -rn 'isDirty'` → only the declaration and the setter). `ParCoolConfig#load` only writes when
   the file does not exist. Net effect: a change made in ParCool's settings screen is lost when the
   game exits. The intent (stop rewriting the whole document on every `set()`, which the settings
   screens call once per rendered frame while a slider is dragged) is sound, but the flush was never
   wired. **Not copied.** This port keeps the 1.21.1 form, `persist() → save(this.file)`, so every
   change is written.
2. **`BufferUtil#ensureRoom` made `static` and given a `static ByteBuffer current` (1.21.11).**
   `BufferUtil` is used from the client tick, the network handler and the server tick; a static
   "buffer currently being wrapped" is a data race across those threads, and it reports the wrong
   buffer's remaining size in the overflow message. In the same commit,
   `putVector3i` and `putVec3` **lost** their `ensureRoom` calls, so a 12- or 24-byte write is no
   longer pre-checked (a raw `BufferOverflowException` instead of the message that names the action and
   the head-room). **Not copied.** This port keeps the 1.21.1 form: an instance method over
   `this.buffer`, with the check on both vector writers.
3. **`com.alrex.parcool.common.damage.DamageSources` is upstream's own class, not a NeoForge patch
   helper.** The 1.21.1 `accesstransformer.cfg` carries `public net.minecraft.world.damagesource.DamageSources *()` for
   it, which is dead: the mod never calls `Entity#damageSources()`. Dropped.

Found **in upstream ParCool / the 1.21.1 port** and *not* fixed here (recorded, not redesigned):

4. **`ZiplineRopeEntity#addAdditionalSaveData` writing `"Tile1_X"` three times.** Already fixed in both
   reference ports; this port inherits the fix (six distinct keys). Recorded because upstream still
   has it.
5. **`KeyBindings#isDown` / `isMetaKeyDown` polling an unbound keysym.** `isPollableKeysym(key)`
   (`key.getValue() >= GLFW_KEY_SPACE`) is in place in both; `GLFW_KEY_UNKNOWN` is `-1`, so without it
   GLFW answers `GLFW_INVALID_ENUM` and the log fills with `65539: Invalid key -1`. Inherited.
6. **`KeyMapping.MAP` holding one mapping per key.** Inherited, together with
   `KeyBindings#restoreVanillaBindings()` and the `KeyRecorder#onClientTick` call that drives it —
   ParCool binds 16 keys vanilla also owns, so without the repair ParCool steals right-click / Space /
   left-Ctrl on Fabric. This is the single most player-visible difference between the 1.21.1 and
   1.21.11 branches, and **1.21.3 is on the `Map<Key, KeyMapping>` side** (verified in the decompiled
   `KeyMapping`), so the repair is live here.
7. **`Action#wantsToShowStatusBar(LocalPlayer, …)`.** Fixed here (parameter is `Player`), but the
   underlying pattern survives elsewhere: `api/Stamina#consume`/`recover` and
   `common/action/impl/BreakfallReady` still reference `LocalPlayer` inside `common`. They are only
   reached from client hooks, and both reference ports ship them, so they were left alone. A
   dedicated-server verifier that is stricter about `instanceof`/`checkcast` would break there.
8. **`Animation#setAnimator(Animator)` vs `setAnimator(Class, Object...)`.** The 1.21.11 port
   changed the second so a class constant (`ldc`) replaces `new XAnimator()` in the `Action`
   implementations, on the theory that the JVM verifier resolves `new` targets and would drag
   `LocalPlayer` onto a dedicated server. **Not copied** — 1.21.3 ships the 1.21.1 form, which
   demonstrably runs a dedicated server on the 1.21.1 branch, and I could not test a server here
   (§6). If a future port ever hits "Attempted to load class net.minecraft.client.player.LocalPlayer
   which is not present on the dedicated server", this is the change to make.
9. **25 unused imports** in `common` (upstream's: `Vec2` in `KeyBindings`, `RandomSource` in
   `BreakfallReady`, `Entity` in `Dodge`, …). Both reference trees carry 24–25 of the same ones, so
   they are upstream's, not this port's. Only the one this port introduced — `LevelAccessor` in
   `ZiplineHookBlock`, a leftover from the 1.21.11 file — was removed. Left alone on purpose: a port
   that rewrites unrelated source is harder to review than one that does not.
10. **`net.minecraft.util.FastColor` is gone** in 1.21.2 and replaced by `ARGB` with the same
    semantics (`ARGB.color(alpha, rgb)` is `alpha << 24 | rgb & 0xFFFFFF`). A mechanical rename, but
    it is silent: the nested class changed name too (`FastColor.ARGB32.red` → `ARGB.red`).

---

## 6. What was NOT verified, and why

* **Nothing was run.** No `runClient`, no `runServer`, no headless client, no Prism instance — the
  task explicitly forbade launching Minecraft. Acceptance for this port is therefore
  `./gradlew build` succeeding from a clean tree plus inspection of the two artifacts:
  - the Fabric jar carries `accessWidener v2 intermediary`, no refmap, `fabric.mod.json` with
    `"version": "1.21.3-3.4.3.3"`, `"minecraft": "~1.21.3"`, and **no mojmap class or descriptor
    string anywhere in `com/alrex/parcool/**`**;
  - the NeoForge jar carries `accessWidener v2 named`, a mojmap-named AT, `META-INF/neoforge.mods.toml`
    with `version = "3.4.3.3"`, and 28 mixin classes.
* **Untested at runtime, in order of how likely they are to matter:**
  1. every mixin target — the `@Inject(method = …)` descriptors were re-derived by reading the
     decompiled 1.21.3 sources, and every one of them was confirmed to exist with the stated
     descriptor, but a target that silently stops matching is only observable on a boot;
  2. `restoreVanillaBindings` — it runs once on the first client tick and its reflection targets
     (`KeyMapping.MAP`, `KeyMapping.ALL`) were confirmed to exist with the expected shapes;
  3. the camera roll path in `CameraAnglesMixin` (1.21.3's `Camera#getUpVector()` / `getLeftVector()`
     do return the live `Vector3f` fields, so in-place rotation is valid — but the visual result is
     unverified, and the 1.21.1 tree's own comment flags it as the one hook that needs eyes on it);
  4. the optional integrations, none of which can be exercised without the mods installed;
  5. the zipline rope render, which needs a placed pair of hooks in a world.

---

## 7. What the next port should not trust

* **The 1.21.11 tree as a base for anything below 1.21.5.** It is a valid base only for the module
  layout, the Gradle/packaging files and the three bug-fix commits. Its `common/src` is written
  against 1.21.5+ APIs in roughly 30 files and will not compile anywhere earlier.
* **The 1.21.1 tree as "a finished port" without diffing first.** It is finished for 1.21.1 and
  correct in shape, but it carries the dead `ClientPacketListenerMixin`, 25 unused imports, stale
  comments copied from the 1.21.11 branch (`neoforge/build.gradle` still says "Paraglider-neoforge-
  21.11.0-beta.6" in a 1.21.1 project), and a `Limitations#listFiles` line that PROMPT.md calls a fix
  the 1.21.11 tree dropped — in fact the two trees are byte-identical there apart from line wrapping,
  so nothing was dropped.
* **The delta table in `PROMPT.md` §6**, for the same reason as §2 above: it only has two columns and
  1.21.3 is on the *new* side of six of the rows that are marked "1.21.1 (old side)".
* **Loom's generated sources jar, for anything about member access** (see §4).
* **The `-Xmx3G` in both reference `gradle.properties` files**, if more than one port builds at once.
* **`/tmp` as a scratch directory in this folder family.** The sibling `parcool-Architectury-API-*`
  agents share it; a `find`/`cmp` snapshot written to `/tmp/foo.txt` here was silently overwritten by
  a neighbouring port mid-run and produced a file list missing `common/item/Items.java` — which is how
  the missing `setId` calls first went unnoticed. Use a per-port subdirectory.

---

## 8. Loom cache / build hygiene on this machine

* `./gradlew --stop`, `pkill` and `killall` are **not** available here: three ports build in parallel
  and their Gradle daemons are shared infrastructure. When a stale Loom remap has to be dropped, do it
  by deleting the cache directories of *this* project only:
  ```bash
  rm -rf .gradle/loom-cache build common/build fabric/build neoforge/build
  ./gradlew :common:build     # Architectury Loom needs :common's jar before it can configure
  ./gradlew build
  ```
  The first invocation on a clean tree must be `:common:build`; `PROMPT.md` §5 says so only
  indirectly ("the very first invocation has to be `./gradlew :common:build`") and the root `build`
  task cannot bootstrap itself, because Loom resolves the `:common` project dependency while it is
  *configuring* `:fabric` and `:neoforge`.

## Recipe `category` field (added by the orchestrator, after the port was built)

All five `data/parcool/recipe/*.json` were missing `category`, which the vanilla
`ShapedRecipe` / `ShapelessRecipe` codec declares as `Codec.fieldOf("category")` --
a *required* field, not an optional one. Verified on this version's mojmap jar with
`javap -c 'net.minecraft.world.item.crafting.ShapedRecipe$Serializer'`: the CODEC builder
uses `Codec.fieldOf` for the key `category` while `group` and `show_notification` go
through `optionalFieldOf`. Without it the datapack loader reports `Missing field category`
and drops the recipe, so every ParCool item is uncraftable in game -- a build-time-clean,
boot-time-broken bug that only shows up in a running client.

`"category": "misc"` was added to all five files. It is harmless on
`parcool:zipline_rope_dye` (ParCool's own `CustomRecipe` codec ignores unknown keys).

This is **inherited, not original**: the same omission exists in the read-only reference
trees `parcool-Architectury-API-1.21.1` and `parcool-Architectury-API-1.21.11`, and it is
inherited from upstream ParCool. Any port starting from those trees will reproduce it.
