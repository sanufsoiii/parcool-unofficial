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
  > *Later phases of this port did run a server* — see §9 (not verified) and §10, where
  > `:fabric:runServer` reached `Done (0.944s)` and the fabric network was reworked. No client was
  > ever launched in this port, so every client-side claim in §10 and below remains unexercised here.
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
* **Architectury's `NetworkChannel`, and its own advice to prefer the id-based API** — see §10.
  On Fabric, `NetworkChannel` registers S2C only when `Platform.getEnvironment() == Env.CLIENT`, so
  on a dedicated server it registers no S2C receiver, logs nothing about it, and every
  server-to-client send then throws `NullPointerException` out of
  `NetworkAggregator.collectPackets`. Symmetrically, `NetworkManager.registerReceiver(S2C, …)` is
  unusable on a dedicated server in **every** Architectury 14–18 line (14.0.4, 15.0.3, 16.1.4,
  17.0.6, 17.0.8, 18.0.5, 18.0.8 all carry `@Environment(EnvType.CLIENT)` on
  `NetworkAggregator.Adaptor#registerS2C`), because Fabric Loader strips such members outside a
  client: the id-based path must register **only the payload type** there, via
  `NetworkManager.registerS2CPayloadType(id)`. Neither a standalone client nor a single player world
  shows either half — the integrated server runs inside the client JVM with `Env.CLIENT` — so the
  dedicated server is the only oracle for this class of defect.
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

## `minecraft:iron_chain` in the rope recipe (fixed by the orchestrator, after the port was built)

`data/parcool/recipe/zipline_rope.json` was shipped with `"C": "minecraft:iron_chain"`.
**No such item exists in 1.21.3**, so the zipline rope was uncraftable — a silent datapack
failure with a green build, the exact class of defect a compile check cannot see.

Proof, from this port's own client resources jar
(`neoforge/build/moddev/artifacts/neoforge-21.3.97-minecraft-resources-aka-client-extra.jar`):

```
unzip -l <jar> | grep -c iron_chain              -> 0
unzip -l <jar> | grep 'models/item/chain.json'   -> assets/minecraft/models/item/chain.json
unzip -l <jar> | grep 'recipe/chain.json'        -> data/minecraft/recipe/chain.json
```

The correct spelling is `minecraft:chain`.

**When does the rename happen?** Verified across the resource jars of the sibling ports:

| version | item in resources jar | correct recipe id |
|---|---|---|
| 1.21.2, 1.21.3 | `chain` | `minecraft:chain` |
| 1.21.5, 1.21.6, 1.21.7, 1.21.8 | `chain` | `minecraft:chain` |
| 1.21.9, 1.21.10 | `iron_chain` | `minecraft:iron_chain` |

So the vanilla `chain` -> `iron_chain` rename lands somewhere in 1.21.9. Do not carry
`iron_chain` backwards and do not carry `chain` forwards; read the target's own resources jar.

This is not a port-local mistake: the read-only base tree
`parcool-Architectury-API-1.21.11` also ships `iron_chain` (correct *there*), and copying that
recipe into any pre-1.21.9 port silently breaks crafting there.

## 9. Defect found by running the game: the NeoForge payload was decoded in a released buffer

Found by launching a client, not by building. **NeoForge only** — see the last subsection for why
this port's Fabric side was already correct and was deliberately left untouched.

### The stack, from the log of a live client

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

and again every few seconds, indefinitely. On NeoForge the equivalent frame is
`NeoForgeParCoolNetwork.lambda$register$0` — the identical one-liner is on both loaders in this
port, and the Fabric trace above is the frame the 1.21.7 client logged.

### Root cause

```java
(buf, context) -> context.queue(() -> handler.accept(erased.decode((RegistryFriendlyByteBuf) buf), context))
```

`NetworkManager.registerReceiver` hands the lambda a **raw** buffer on the network thread and
releases it as soon as the lambda returns. This is not inferred from the log; it is what
Architectury 14.0.4 (the version this port pins) does in
`dev.architectury.impl.NetworkAggregator#registerReceiver(Side, ResourceLocation, List, NetworkReceiver)`:

```java
registerC2SReceiver(type, BufCustomPacketPayload.streamCodec(type), packetTransformers, (value, context) -> {
    class_9129 buf = new class_9129(Unpooled.wrappedBuffer(value.payload()), context.registryAccess());
    receiver.receive(buf, context);   // <- ParCool's lambda runs here, buf is still alive
    buf.release();                    // <- and is freed the moment the lambda returns
});
```

`PacketContext#queue` is `taskQueue.execute(runnable)` — it **defers**. On the server that is the
main-thread task queue, so the runnable body runs strictly *after* the lambda returned and *after*
`buf.release()`. The decode then reads a `ByteBuf` whose `refCnt` is already 0, which is exactly
what `VarLong.read` -> `AbstractByteBuf.readByte` checks. The first ParCool packet in each direction
kills the server task, the limitation snapshot never arrives, and no action can start.

`queue` exists for **thread safety of the handler**, not for decoding. The handler touches the
player, the level and ParCool's own state, all main-thread-only; the decode is pure buffer
arithmetic with no thread affinity of its own.

### The fix

Decode on the network thread, while the buffer is alive; queue only the handler.

```java
(buf, context) -> {
    T payload = erased.decode((RegistryFriendlyByteBuf) buf);
    context.queue(() -> handler.accept(payload, context));
}
```

The comment in the source restates this causal chain deliberately. Without it the next reader sees
"the decode is just a local read, inline it again" and reinstates the bug — the two forms are
identical in intent and differ only in statement order.

`javap -p -c` on the class inside `neoforge/build/libs/parcool.jar` shows `decode` at offset 2 and
`queue` at offset 22 of one synthetic method, plus a second synthetic method whose whole body is a
single `BiConsumer.accept` — the queued runnable no longer touches the buffer.

### Why this port's Fabric side was already correct and was left alone

> **Superseded by §10.** The decode order below was right and survived. The conclusion that the
> `NetworkChannel` shape as a whole was "already correct" was wrong: on a dedicated Fabric server it
> registers no S2C receiver at all and cannot send a single server-to-client packet. §10 replaces it
> with the id-based path and a server-side guard.

`fabric/src/main/java/com/alrex/parcool/platform/FabricParCoolNetwork.java` goes through
`NetworkChannel` rather than the raw `NetworkManager`, and `NetworkChannel` already has the right
order. In architectury 14.0.4, `dev.architectury.networking.NetworkChannel#register`:

```java
info.messageConsumer.accept(info.decoder.apply(buf), () -> context);   // decode, then invoke
```

with `info.decoder` being the `buffer -> typed.decode((FriendlyByteBuf) buffer)` lambda this port
passes in. Architectury calls the decoder **synchronously**, on the network thread, and hands the
already-decoded payload to `messageConsumer` — which in this port is
`(payload, context) -> context.get().queue(() -> handler.accept(payload, context.get()))`. So on
Fabric: decode on the network thread, then queue only the handler. Exactly the shape the NeoForge
side now has.

No change was made to that file. Note that `context` there is a `Supplier<PacketContext>` and
`context.get()` is called twice — the second call happens later, on the main thread, but the
`PacketContext` is a stable object built once per packet, so this is not a lifetime hazard. It was
left as-is deliberately rather than "improved", since it is not the defect.

### This class of defect is invisible to the compiler and only a live client catches it

**`./gradlew build` is green on the broken code and stays green on the fixed code.** Nothing is wrong
to compile: `buf` is a live `RegistryFriendlyByteBuf` parameter, `decode` accepts it, and capturing
it in a nested lambda is legal Java. The refcount is a *runtime* netty property; the window in
which the buffer is valid is a *lifetime* property of Architectury's `registerReceiver` contract.
Neither is expressible in the type system, so javac has nothing to complain about.

It is equally invisible to a test: without a client that has **joined a world**, no ParCool packet is
ever sent, the receiver lambda is never invoked, and the released-buffer read never happens. This
port's entire verification story — `checkCommonLoaderIndependence`, `:common:build`, `./gradlew
build`, mixin-target checks against the real jars, unzipping the distributables — passes just as
happily on code that dies on the first packet.

The only oracle that finds this class of defect is a client a human launched, entering a world, with
a server at the other end. Compilation, build output, jar contents and mixin validation are all
necessary and none is sufficient.

### What was NOT verified

**The game was not launched for this fix** — no `:fabric:runClient`, no `:neoforge:runClient`, no
server. The evidence is Architectury's own source for the buffer contract, `javap` on the built
class, and a green build. "The exception is gone" and "actions can now start" are *expected* from
that evidence, not observed. The 1.21.3 Fabric jar is byte-for-byte the one published before this
change (`fd1bc39e815fbe1aedc9462b123cbe1469c50246e242fc68aa71f7c3c693574e`) — nothing in it was
touched, which is the point.

### Artifacts for this fix

```
0.1-mc1.21.3fabric-3.4.3.3.jar     sha256 fd1bc39e815fbe1aedc9462b123cbe1469c50246e242fc68aa71f7c3c693574e
0.1-mc1.21.3neoforge-3.4.3.3.jar   sha256 0cb84a6135bdbcb6dd818a11e451e7dda962f7177fa287a04ffcd5ad81963bc0
```

The **Fabric** hash is byte-for-byte what was published before this change
(`fd1bc39e815fbe1aedc9462b123cbe1469c50246e242fc68aa71f7c3c693574e`) — that file is correct already
and nothing in it was touched, which is exactly why the rebuild produced an identical jar.

The **NeoForge** hash differs from the previously published `37c834b0…`: that jar contained the
released-buffer decode, this one decodes on the network thread. Code changed, so the hash must
change.

The game was not launched to validate either.

---

## 10. Defect found by a live dedicated server: the Fabric side registered no S2C receiver at all

*(This supersedes §9's "Why this port's Fabric side was already correct and was left alone". That
paragraph was right about the one thing it looked at — the decode order — and wrong about the rest:
the `NetworkChannel` shape it endorsed cannot send a single server-to-client packet from a dedicated
Fabric server. Two different defects lived in the same method.)*

### What the player saw

A dedicated Fabric server, this jar, a real client on the other end:

```
[Error] Failed to synchronize ParCool Limitation. There may be problems about server connection.
[Render thread/WARN] (ParCool) [parcool] the server limitation snapshot has been missing for 10s,
  so no action can start. Asking the server again (ParCoolIsActive=true).
[Render thread/WARN] (ParCool) Detected ParCool Limitation is not synced.
  Sending synchronization request...
```

The client asked, the server never answered, the client asked again — forever. No action can start
because `ParCoolIsActive` never becomes `true`.

### The counters, from the same two logs

`fabric/run/logs/2026-09-30-3.log.gz` (server, 11:57:23) versus
`fabric/run/logs/2026-09-30-4.log.gz` (client, 11:57:38):

| | `Registering C2S receiver` | `Registering S2C receiver` | `AbstractMethodError` |
|---|---|---|---|
| dedicated server | **8** | **0** | 0 |
| client | 8 | 9 | 0 |

The server registered **no** S2C receiver. The client's 9 is 8 ParCool ids plus
`architectury:spawn_entity_packet`, which Architectury registers for itself — the ids on the client
were the channel-derived `parcool:main/<hash>` ones, so the two sides never even agreed on a name for
the same message, only on a hash both happened to compute.

### Root cause, from the bytecode

`javap -c` on `dev/architectury/networking/NetworkChannel.class` inside
`architectury-fabric-14.0.4.jar`, method `register`:

```
 77: invokestatic  dev/architectury/networking/NetworkManager.c2s()
 87: invokevirtual dev/architectury/networking/NetworkManager.registerReceiver(Side, ResourceLocation, NetworkReceiver)
 90: invokestatic  dev/architectury/platform/Platform.getEnvironment()
 93: getstatic     dev/architectury/utils/Env.CLIENT
 96: if_acmpne     112            <-- everything below is client only
 99: invokestatic  dev/architectury/networking/NetworkManager.s2c()
109: invokevirtual dev/architectury/networking/NetworkManager.registerReceiver(Side, ResourceLocation, NetworkReceiver)
112: return
```

`NetworkChannel` registers C2S unconditionally and S2C **only when the environment is CLIENT**. On a
dedicated server `NetworkAggregator.S2C_TYPE`, `S2C_CODECS` and `S2C_TRANSFORMERS` therefore stay
empty, and the failure is deferred to the first send. `NetworkAggregator.collectPackets(sink, S2C,
id, buf)` does `new BufCustomPacketPayload(S2C_TYPE.get(id), bytes)` — with `null` — and the
following `collectPackets(sink, S2C, payload, access)` dereferences `payload.type().id()`.

The pre-fix code did exactly that, and the orchestrator's own log of the 11:57 session has it:

```
java.lang.NullPointerException: Cannot invoke "net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type.id()" because "type" is null
	at dev.architectury.impl.NetworkAggregator.collectPackets(NetworkAggregator.java:143)
	at dev.architectury.impl.NetworkAggregator.collectPackets(NetworkAggregator.java:137)
	at dev.architectury.networking.NetworkManager.toPacket(NetworkManager.java:108)
	at dev.architectury.networking.NetworkChannel.toPacket(NetworkChannel.java:90)
	at dev.architectury.networking.NetworkChannel.sendToPlayers(NetworkChannel.java:100)
	at com.alrex.parcool.platform.FabricParCoolNetwork.sendToPlayers(FabricParCoolNetwork.java:68)
	at com.alrex.parcool.common.network.NetworkRegistries.sendToPlayers(NetworkRegistries.java:150)
	at com.alrex.parcool.common.network.payload.ClientInformationPayload.lambda$handleServer$4(ClientInformationPayload.java:65)
```

That is the whole player-visible symptom: the client sends `ClientInformationPayload` on join, the
server handles it, the handler broadcasts the limitation snapshot, the broadcast throws, the tick
carries on, the server stays up. **A green `Done (…)` hides this completely** — the server is
"healthy" and the network is dead.

The port's own javadoc had recorded the opposite conclusion ("`NetworkChannel` is the working path
there, because on Fabric a payload type is registered once and serves both directions"). That is true
on a client and false on a dedicated server, and the claim was never tested on one.

### Why not the obvious id-based path

`NetworkManager.registerReceiver(S2C, id, receiver)` reaches
`NetworkAggregator.Adaptor#registerS2C`, and `javap -v` on
`dev/architectury/networking/fabric/NetworkManagerImpl$1.class` shows that method carrying

```
RuntimeInvisibleAnnotations:
  0: #119(#120=e#121.#122)
    net.fabricmc.api.Environment(
      value=Lnet/fabricmc/api/EnvType;.CLIENT
    )
```

It is the **only** member of that interface with the annotation — `registerC2S`, `registerS2CType`,
`toC2SPacket` and `toS2CPacket` have none. Fabric Loader's `EnvironmentStripper` deletes
`@Environment(CLIENT)` members on a dedicated server, so the aggregator's
`ADAPTOR.get().registerS2C(…)` throws `AbstractMethodError`. On a client the method survives, which
is why the client and single player never show it.

### The variant chosen: id-based with a server-side guard (variant 2)

`fabric/src/main/java/com/alrex/parcool/platform/FabricParCoolNetwork.java` now mirrors the NeoForge
implementation, with one extra branch:

```java
if (clientbound && Platform.getEnvironment() == Env.SERVER) {
    NetworkManager.registerS2CPayloadType(wireId);
    return;
}
```

* **Variant 1 (keep `NetworkChannel`, add a server-side `registerS2CPayloadType(wireId)`) is not
  merely less tidy here, it does not work.** The id `NetworkChannel` registers and sends under is
  `parcool:main/<uuid-name-hash-of-the-class-name>`, not the `parcool:payload.*` id `common` hands
  down — `NetworkChannel#register` builds it from
  `UUID.nameUUIDFromBytes(clazz.getName().getBytes(UTF_8))` joined to the channel id with `"/"`, and
  exposes no accessor for it. Registering `parcool:payload.limitation` on the server would leave
  `S2C_TYPE.get("parcool:main/d4a46f0d…")` null, and the very same NPE would be thrown on the first
  send. Making variant 1 work means re-deriving Architectury's private id derivation in our code and
  hoping no Architectury release changes the recipe.
* Variant 2 also removes the loader's own asymmetry from the picture: the wire ids become the same
  `parcool:payload.*` (+ `.c2s`) names NeoForge already uses, so registration and sending address a
  message by the same key on both loaders, and one set of ids is one thing to reason about.
* **C2S did not regress.** It goes through `NetworkManager.registerReceiver(C2S, …)` exactly as
  before, which calls the unstripped `Adaptor#registerC2S` and works on both sides.
* **The decode order survived the rewrite.** As in §9, the decode happens inside the receiver lambda,
  on the network thread, while `registerReceiver` still owns the buffer; only the handler is queued.
  The old `NetworkChannel` shape got this for free (Architectury called the decoder synchronously
  before handing the payload to the consumer); the id-based shape has to do it explicitly, and it does.
* **The `Set<Class<?>> registered` guard is gone, deliberately.** It existed only because
  `NetworkChannel` keys its private `encoders` map by payload class, so the second registration of a
  bidirectional message was silently swallowed by `Map.put` and the guard existed to stop that from
  looking like a success. With ids, `NetworkRegistries` gives every direction its own key, so there is
  nothing to de-duplicate; `registerPayloads` already guards re-entry with `payloadsRegistered`, and
  Fabric's `PayloadTypeRegistry.register` throws on a duplicate type id, so a double registration
  cannot pass silently.
* **The wire format is unchanged.** Both `registerReceiver` and `registerS2CPayloadType` hand the
  loader the same aggregator payload — `S2C_TYPE[id] = new Type(id)` plus
  `BufCustomPacketPayload.streamCodec(type)` — and the real payload codec only ever runs locally. A
  packet still travels as `BufCustomPacketPayload(id, <the bytes ParCool encoded>)`. What does change
  is the id itself (`parcool:main/<hash>` → `parcool:payload.*`); both ends of a connection run this
  jar, so they still agree, and the NeoForge side now uses the identical names.

### Evidence from the server log

`:fabric:runServer`, Loom cache deleted first, `fabric/run/logs/2026-09-30-5.log.gz` and
`/tmp/opencode/srv-1.21.3.log`:

```
[12:15:11] [Server thread/INFO] (Minecraft) Starting Minecraft server on *:25573
[12:15:12] [Server thread/INFO] (Minecraft) Done (0.944s)! For help, type "help"
```

No `AbstractMethodError`, no `NullPointerException`, no `IllegalReferenceCountException` anywhere in
the run.

The counters now match the design instead of contradicting it: **4** `Registering C2S receiver`
(one C2S-only message plus the three bidirectional ones), **0** `Registering S2C receiver` — correct
for a dedicated server, which has no use for an S2C *receiver* — and 7 clientbound payload types
registered through `registerS2CPayloadType`:

```
[12:15:09] [main/INFO] (ParCool) [parcool] network: serverbound payload parcool:payload.custom_stamina.c2s: receiver and sendable type both in place
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.start_breakfall_event on a dedicated server: type only, no inbound handler
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.limitation on a dedicated server: type only, no inbound handler
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.stamina.broadcast on a dedicated server: type only, no inbound handler
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.action_state.broadcast on a dedicated server: type only, no inbound handler
[12:15:09] [main/INFO] (ParCool) [parcool] network: serverbound payload parcool:payload.action_state.c2s: receiver and sendable type both in place
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.action_state on a dedicated server: type only, no inbound handler
[12:15:09] [main/INFO] (ParCool) [parcool] network: serverbound payload parcool:payload.client_info.c2s: receiver and sendable type both in place
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.client_info on a dedicated server: type only, no inbound handler
[12:15:09] [main/INFO] (ParCool) [parcool] network: serverbound payload parcool:payload.stamina.c2s: receiver and sendable type both in place
[12:15:09] [main/INFO] (ParCool) [parcool] network: clientbound payload parcool:payload.stamina on a dedicated server: type only, no inbound handler
```

4 + 7 = 11, which is exactly what `NetworkRegistries.registerPayloads` asks for. **Those seven lines
are also the fix's only new observability**, and they were added for a reason: Architectury's
`Registering S2C receiver with id …` line lives in `registerS2C`, the method the environment stripper
removes, and `NetworkAggregator.registerS2CType` / `Adaptor.registerS2CType` log nothing at all
(verified with `javap -c`). So a dedicated Fabric server using `registerS2CPayloadType` is *silent*
about its clientbound payloads — which is precisely the silence that let this defect ship. The
wording deliberately avoids Architectury's `Registering … receiver with id` phrasing so the two cannot
be conflated when counting registrations in a log.

### Warning for the next port

**`@Environment(EnvType.CLIENT)` on `NetworkAggregator.Adaptor#registerS2C` is stripped by Fabric
Loader on a dedicated server in every Architectury line from 14 to 18.** Checked by `javap -v` on
`NetworkManagerImpl$1` in `architectury-fabric` 14.0.4, 15.0.3, 16.1.4, 17.0.6, 17.0.8, 18.0.5 and
18.0.8: the annotation is present in all of them, and it is on `registerS2C` only. Consequences for
any port that uses Architectury networking on Fabric:

1. **On a dedicated server, the id-based path must register only the payload type, never the
   receiver.** `NetworkManager.registerS2CPayloadType(id)` (or the `Type`+codec overload) goes
   through the unstripped `Adaptor#registerS2CType`, fills `S2C_TYPE`/`S2C_CODECS`/`S2C_TRANSFORMERS`,
   and reaches Fabric as `PayloadTypeRegistry.playS2C().register(...)`. Calling
   `registerReceiver(S2C, …)` there is an immediate `AbstractMethodError` at mod init.
2. **Do not use `NetworkChannel` on a dedicated server.** It is the API Architectury recommends and
   it is what most tutorials use, and it silently registers nothing for S2C outside a client. There is
   no error, no warning, no log line — the server boots, and every server-to-client send throws
   `NullPointerException` later, from `NetworkAggregator.collectPackets`.
3. **A dedicated server is the only thing that finds either defect.** Single player hosts the
   integrated server inside the client JVM, `Env.CLIENT`, so the stripper never runs and
   `NetworkChannel` registers both directions. A standalone client likewise keeps `registerS2C`. Both
   are green. `./gradlew build` is green. The failure needs a *separate* server process.
4. **Both symptoms look like something else.** "Server can't send" reads as a config or
   `online-mode` problem; `AbstractMethodError` on the id-based path reads as a version skew or a
   stale jar — it is neither, the method is declared with exactly the descriptor the interface
   declares. Read the class file before touching a version number.
5. **The buffer contract is the other half.** Whichever registration shape you pick, decode inside
   the receiver lambda, before `context.queue(...)`; see §9.

### What was NOT verified

* **The client was not launched** — the orchestrator holds the single GPU and owns client-side
  acceptance. The client-side branch of the new code (`registerReceiver(S2C, …)` on a client) is
  therefore unexercised *by this port*; the identical branch is proven live on 1.21.2, which uses
  the same Architectury 14.0.4 and the same code.
* **No S2C packet was actually delivered.** The server booted with no player connected, so the send
  path past `NetworkAggregator.collectPackets` (the exact place that used to NPE) was not executed
  live here. What is verified statically: `registerS2CPayloadType(id)` provably populates the map
  `collectPackets` reads (`javap -c` on `NetworkAggregator.registerS2CType`), and the 11-line
  registration log shows all 7 clientbound ids went through it without a duplicate-type error.
  The pre-fix NPE was captured live on this very port, so the failure mode is known — its absence is
  inferred from the registration log, not observed end to end.
* **Nothing else moved**: the NeoForge jar's bytecode is unchanged (see below), and no mixin,
  payload, recipe or registry was touched.

### Artifacts for this fix

```
0.1-mc1.21.3fabric-3.4.3.3.jar     sha256 bdd19800342faf5d053b467b8b7b049ce580d281dbfc2aa4e2d2810829024589
0.1-mc1.21.3neoforge-3.4.3.3.jar   sha256 d2804fd114abb5717d6a0b5da82d13680bbf4e9bf11ee92f94b0636adb3cb80b
```

The **Fabric** hash necessarily changed (it is the load that carries the fix). The **NeoForge** hash
also changed, but only in metadata: `NetworkRegistries` and `ParCoolNetwork` differ between the two
jars because their javadoc grew, which shifts `LineNumberTable`/`SourceFile`. `javap -p -c` on both
versions of both classes is **byte-identical** — the NeoForge runtime behaviour is exactly what it
was at `0cb84a6135bdbcb6dd818a11e451e7dda962f7177fa287a04ffcd5ad81963bc0`.

The server was launched for this fix (`Done (0.944s)`, `server-port=25573` in
`fabric/run/server.properties` so it does not fight the sibling ports for 25565). The client was not.

## NeoForge crash on world entry: the access transformer was not mirroring the widener (live client)

A live NeoForge client created a world, the player joined, and the world died on the first
player tick:

```
[Server thread/ERROR] [ne.ne.bu.EventBus/EVENTBUS]: Exception caught during firing event:
  class com.alrex.parcool.common.action.AdditionalProperties tried to access private field
  net.minecraft.world.entity.Entity.onGround
java.lang.IllegalAccessError: ... AdditionalProperties.onTick(AdditionalProperties.java:29)
  at TRANSFORMER/parcool@3.4.3.3/com.alrex.parcool.common.action.ActionProcessor.onTick(ActionProcessor.java:77)
  at TRANSFORMER/architectury@.../dev.architectury.event.forge.EventHandlerImplCommon.event(EventHandlerImplCommon.java:144)
  at TRANSFORMER/neoforge@.../net.neoforged.neorge.event.EventHooks.firePlayerTickPost(EventHooks.java:961)
[Server thread/ERROR] [minecraft/MinecraftServer]: Encountered an unexpected exception
net.minecraft.ReportedException: Ticking player
```

**Cause.** `neoforge/src/main/resources/META-INF/accesstransformer.cfg` did not mirror
`common/src/main/resources/parcool.accesswidener`. The old comment claimed the members are
"already public there", based on `javap` against the NeoForge runtime artifact. The check was
accurate and the conclusion was wrong: those bytes are the state *before* NeoForge applies its own
access transformers, and at launch the classes are transformed in memory, after which the members
are private. The Fabric client never showed it, because the widener already widens all of them.

**The rule this establishes, for every port:** an AT entry for an already-public member is
harmless; a missing entry for a member that ends up private is a crash the moment a player ticks.
The cost is asymmetric, so the AT must duplicate the widener rather than be a "residual" of it.
"Already public in the jar" is not evidence that an AT entry is unnecessary.

**Second, independent lesson.** An access transformer and an access widener are the same list
written twice, and the two can drift apart with nothing to notice: compilation only needs one of
them, the Fabric side only exercises the other, and the drift is invisible until a NeoForge player
ticks. If you widen a member for Fabric, check the NeoForge file in the same commit.

**For this port:** Added the four missing entries: Entity#onGround, LivingEntity#swimAmount,
LivingEntity#swimAmountO, Player#canPlayerFitWithinBlocksAndEntitiesWhen.
