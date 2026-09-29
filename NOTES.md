# NOTES — ParCool Architectury API port, Minecraft 1.21.6

Working notes for this port: the resolved toolchain and where each number came from, every version
delta that had to be decided, the bugs found, and what the next port should not trust.

Read-only references: `../parcool-Architectury-API-1.21.11` (the base this tree was imported from)
and `../parcool-Architectury-API-1.21.1` (the older, also finished port). Neither was modified.

---

## 1. Toolchain

Every number below was resolved from a live metadata endpoint or a local cache, not copied.

| Setting | Value | Where it came from |
|---|---|---|
| `minecraft_version` | `1.21.6` | target |
| `neo_version` | `21.6.20-beta` | last release of the 21.6 line, `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` (21.6.0-beta … 21.6.20-beta; the line is beta-only, 1.21.6 shipped no NeoForge stable) |
| `loader_version` | `0.16.10` | `https://meta.fabricmc.net/v2/versions/loader` — the Fabric Loader of the 1.21.6 era. Architectury 17.0.6 asks for `>=0.15.4`, so this is comfortably in range. |
| `fabric_api_version` | `0.128.2+1.21.6` | `https://api.modrinth.com/v2/project/fabric-api/version?game_versions=["1.21.6"]` — newest 1.21.6 build |
| `architectury_api_version` | `17.0.6` | `https://maven.architectury.dev/dev/architectury/architectury-fabric/maven-metadata.xml`, then each candidate jar's `fabric.mod.json` `depends.minecraft`: 17.0.3/17.0.4 → `~1.21.6~`, **17.0.6 → `~1.21.6`**, 17.0.8 and 18.0.x → `~1.21.7`. 17.0.6 is the newest release on the 1.21.6 line. |
| `dev.architectury.loom` | `1.17.493` | kept from the 1.21.11 tree. **Verified it configures 1.21.6**: the Loom metadata is at `…/dev/architectury/architectury-loom/maven-metadata.xml`, *not* `…/dev/architectury/loom/…` — the URL in PROMPT.md §4 404s, so that line of the brief is wrong. |
| `net.neoforged.moddev` | `2.0.90` | ModDevGradle 1.0.x only covers NeoForge 21.1–21.4; 21.6 needs the 2.x line, and 2.0.90 (published 2025-06-05, i.e. the 1.21.6 release week) is the closest to the target. **Verified working** — `:neoforge:compileJava` configures, runs `applyNeoforgePatches` and compiles against 21.6.20-beta. |
| Gradle wrapper | `9.4.1` | kept from the 1.21.11 tree (Loom 1.17.x requires Gradle ≥ 9) |
| Java toolchain | 21 | `version.json` in the 1.21.6 client jar: `"java_version": 21` |

Versions that do **not** exist, so nobody goes looking for them: ModDevGradle `1.0.22` and `1.0.25`
are absent from the 1.0.x line (1.0.21 → 1.0.23 → 1.0.24 → 2.0.1-beta). Architectury Loom has no
`1.15`/`1.16` line at all (1.14.476 → 1.17.477). The ModDevGradle *plugin marker* is
`dev.architectury`-style path `net/neoforged/moddev/net.neoforged.moddev.gradle.plugin`, **not**
`net/neoforged/moddev-gradle/…` — the latter 404s, again a PROMPT.md line that is wrong.

The ModDevGradle-1.0.x-on-Gradle-9 "Could not find com.mojang:jtracy" trap from the neighbouring
ports does **not** apply here: 2.x registers the Mojang repositories itself, and `neoforge/build.gradle`
needed no extra repository block.

## 2. Which side of each seam 1.21.6 is on

Everything below was checked with `javap` against the mojmap jar
(`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.6-…/…jar`).
The decompiled-sources jar in the Loom cache is generated **with this project's access widener
already applied**, so it lies about visibility — `javap` is the only authority.

| Area | 1.21.6 | Evidence |
|---|---|---|
| `ResourceLocation` | **old** | `net/minecraft/resources/ResourceLocation.class` present, no `Identifier.class` |
| `Registry#get` | **new** (Optional) | `Optional<Holder$Reference<T>> get(ResourceLocation)`; `getValue`/`getValueOrThrow` added |
| `ValueInput` / `ValueOutput` | **new** | `Entity#save(ValueOutput)` / `#load(ValueInput)`; `Player#addAdditionalSaveData(ValueOutput)` |
| `BlockEntityType.Builder` | **deleted** | `net/minecraft/world/level/block/entity/BlockEntityType$Builder` does not exist → `BlockEntityTypeInvoker` + `ParCoolPlatform#registerBlockEntityType` stay |
| `RenderType` / `RenderStateShard` | **old** | `RenderStateShard` still there; `RenderType.create(String, int, RenderPipeline, CompositeState)` is package-private; no `RenderSetup` class at all. But `RenderPipelines` / `RenderPipeline` / `RenderPipeline.Snippet` **do** exist (1.21.5 moved culling/format/shaders out of the shards). |
| Entity render states | **new** | `EntityRenderer<T, S extends EntityRenderState>`, `createRenderState` / `extractRenderState(T,S,float)` / `render(S,PoseStack,MultiBufferSource,int)`. **Package is `net.minecraft.client.renderer.entity.state`, and there is no `AvatarRenderState`, no `AvatarRenderer` and no `SubmitNodeCollector`** — those are 1.21.9+/1.21.11. This is the `PlayerRenderState` shape the 1.21.4 tree already uses. |
| `KeyMapping` | **old** | category is a `String`; `MAP` is `Map<Key, KeyMapping>`; there is no `KeyMapping.Category` record. → `KeyBindings#restoreVanillaBindings` and its `KeyRecorder#onClientTick` driver are **required**, taken from the 1.21.4 tree. |
| `KeyMapping#key` | **private** | `@Shadow private`, not `protected` (1.21.11 widened it) |
| Recipe ingredients | **string form** | `Ingredient.CODEC` = `HolderSetCodec.create(Registries.ITEM, Item.CODEC, false)`, which is `Codec.either(TagKey.hashedCodec, ExtraCodecs.compactListCodec(...))` → accepts `"minecraft:chain"`, `"#minecraft:logs"` and `["a","b"]`, and **rejects** `{"item": …}` / `{"tag": …}`. Vanilla 1.21.6 `data/minecraft/recipe/chain.json` writes `"I": "minecraft:iron_ingot"`. The `result` object is the new `{"count", "id"}` form. |
| `isInWaterOrBubble` | removed | `EntityUtil` keeps the 1.21.11 re-implementation |
| `Player#canInteractWithEntity` | **present** (`(Entity,double)` and `(AABB,double)`) | `PlayerInteractionVisibilityMixin` uses the 1.21.4 form, not `getVisibilityPercent` |
| `jumpFromGround` | on `LivingEntity` | `LivingEntityJumpMixin` stays |
| `Entity#hurt` | **new** | `public final void hurt(DamageSource,float)`, `hurtOrSimulate`, `abstract hurtServer`; `LivingEntityFallMixin`'s redirect target `LivingEntity.hurt(DamageSource,F)V` is correct and the call is `invokevirtual` at offset 45 of `causeFallDamage` |
| `Player#causeExtraKnockback` | **does not exist** | 1.21.11 extracted the knockback block out of `Player#attack`; in 1.21.6 the `setSprinting(false)` is still inline (the only `setSprinting` call site in the whole `Player` class, at offset 635 of `attack`). `PlayerMixin` therefore wraps `attack`. |
| `Item.Properties#setId` | **required** | `effectiveDescriptionId()`/`effectiveModel()` do `Objects.requireNonNull(this.id, "Item id not set")`. Architectury 17.0.6's `RegistrarImpl#register` is literally `Registry.register(delegate, id, supplier.get())` — it never touches the properties, so the key is set by hand in `Items`/`Blocks` (as the 1.21.11 tree already does). |
| `BlockItem#getDescriptionId` | **removed** | `BlockItem` has no override; `Item#getDescriptionId()` is `final` and returns the field set from the properties. The default is `item.<id>`, so ParCool's lang keys stay `item.parcool.*` and no `useBlockDescriptionPrefix()` is needed. `assets/parcool/items/*.json` **is** needed: `effectiveModel()` resolves to `assets/<ns>/items/<path>.json` in 1.21.6 (same as 1.21.4+), and vanilla `assets/minecraft/items/grass_block.json` confirms the shape. |
| `pack.mcmeta` | `pack_format` + `supported_formats` | `PackMetadataSection` is a 3-field record: `description`, `pack_format` (int), `supported_formats` (`InclusiveRange<Integer>`, **optional**). `min_format` / `max_format` from the 1.21.11 tree are not part of the codec in 1.21.6. `InclusiveRange.INT` uses the field names `min_inclusive` / `max_inclusive`. 1.21.6's `version.json`: `pack_version = {resource: 63, data: 80}`; a mod jar is read as both, so `pack_format: 63` with `supported_formats: {63 … 80}`. `Pack#getDeclaredPackVersions` only warns if `pack_format` is outside the range. |
| `InteractionResult` | interface | matches the 1.21.11 tree |
| `Screen` input | **old** | `GuiEventListener`: `keyPressed(int,int,int)`, `mouseClicked(double,double,int)`, `mouseScrolled(double,double,double,double)`, `resize(Minecraft,int,int)`. There is no `net.minecraft.client.input` package in 1.21.6. |
| `GuiGraphics` tooltips | `setComponentTooltipForNextFrame` | the immediate `renderComponentTooltip(Font,List,int,int)` of 1.21.4 is gone; `setComponentTooltipForNextFrame` exists |
| `PlayerModel` | `net.minecraft.client.model.PlayerModel` | not `…model.player` (that move is 1.21.9+) |
| `BlockBehaviour.Properties` | `noCollission()` | 1.21.6 still has the double-s typo; the single-s spelling arrives later |
| `Commands.LEVEL_GAMEMASTERS` | plain `int` | `CommandSourceStack#permissions()` is gone; use `hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| `Level#isClientSide` | field | `isClientSide()` accessor arrives later |
| `AbstractSoundInstance#getLocation` | | `getIdentifier()` is gone |
| `ItemTintSources#ID_MAPPER` | present, private static | so `ItemTintSourcesAccessor` stays; Architectury 17.0.6's `ColorHandlerRegistry` has **no** `registerItemColors`, so it had to be added there |
| `DamageSources` | accessors public | but they are dropped from the runtime by NeoForge's own patch, hence the AT entry `DamageSources *()` |

## 3. Bugs found and fixed

1. **`minecraft:iron_chain` does not exist.** The 1.21.11 tree's `data/parcool/recipe/zipline_rope.json`
   asks for `"C": "minecraft:iron_chain"`; the vanilla item is `minecraft:chain`. The rope was
   uncraftable in the 1.21.11 port. Fixed here to `minecraft:chain`.
2. **`KeyMapping.MAP` repair was missing.** 1.21.6 keeps one mapping per physical key, so ParCool's 16
   bindings evict vanilla's `keyUse` / `keySprint` / … . `KeyBindings#restoreVanillaBindings` and the
   `KeyRecorder#onClientTick` call that drives it were restored from the 1.21.4 tree.
3. **`ConfigSpec#persist()` was a no-op.** The 1.21.11 version only sets `dirty = true`, which nobody
   reads, so every GUI change was lost on restart. The 1.21.1 version, which actually writes the file,
   was taken instead. **The right fix is a debounced write, not a synchronous one on every keystroke —
   that is a redesign and is deliberately not done here.**
4. **`BufferUtil` was a regression.** In the 1.21.11 tree `ensureRoom` was made `static` over a
   non-initialisable `current` (a data race) and the overflow checks were deleted from
   `putVector3i` / `putVec3`. The 1.21.1 version was taken instead.
5. **`RenderType#create` descriptor in the access widener.** Its real return type is
   `RenderType$CompositeRenderType`, not `RenderType`; Loom's `validateAccessWidener` rejects a
   wrong descriptor outright, which is how the difference was caught.
6. **`PlayerMixin` targeted a method that does not exist in 1.21.6** (`causeExtraKnockback`,
   a 1.21.11 extraction). Retargeted to `attack`, with a comment saying why.

## 4. Found but NOT fixed (deliberately)

* **`Animation#setAnimator(Class, Object...)` dedicated-server verifier guard.** The 1.21.11 tree
  moved to `Class`-literal call form. Not re-checked for 1.21.6 — the tree builds and no verification
  log was available without launching the game (see §6). Left as the 1.21.11 tree has it, which is
  the safe form.
* **`Limitations` filename validation.** PROMPT.md claims 1.21.11 removed it. Re-checked: the two
  trees are byte-identical here, the validation **is** present in both, and it is present here too.
  Nothing to do — but the brief is wrong on this point.
* The 25 unused imports are upstream (present in both reference trees); only imports this port
  introduced were removed.

## 5. What the next port should not trust

* **`minecraft-merged-*-sources.jar` in the Loom cache lies about visibility.** It is generated with
  the project's access widener already applied. Use `javap` on the mojmap/named jar.
* **`maven.architectury.dev/dev/architectury/loom/maven-metadata.xml` 404s.** The artifact is
  `architectury-loom` under the same group.
* **`net.neoforged.net/.../moddev-gradle/net.neoforged.moddev.gradle.plugin/` 404s.** The marker lives
  under `net/neoforged/moddev/`.
* **The version-number table in PROMPT.md is not a lookup.** For 1.21.6 both the recipe-ingredient
  format and the render layer sit on the *same* side as 1.21.11 for one and the same side as 1.21.1
  for the other: recipes are string-form (1.21.11 side) while `RenderType` is `RenderStateShard`-based
  (1.21.1 side). 1.21.6 is a hybrid in a direction the table does not describe.
* **`./gradlew build` does not work from a clean checkout.** Loom resolves the `:common` jar while it
  *configures* `:fabric`, so the first invocation must be `./gradlew :common:build`. This is
  inherited from the 1.21.11 tree, not a 1.21.6 bug.
* **Loom's remap cache goes stale after editing a `:common` mixin.** Symptom:
  `Cannot remap causeExtraKnockback because it does not exist in any of the targets [net/minecraft/class_1657]`
  *after* the source has already been corrected — the loader module is still reading the previous
  remapped copy. Clear with
  `rm -rf .gradle/loom-cache common/build/loom-cache common/build/devlibs fabric/build/loom-cache neoforge/build/explodedCommon .architectury-transformer`
  and then run `:common:build` **before** `:fabric:compileJava`. Never `./gradlew --stop` on a shared
  machine — it kills the other ports' daemons.
* **`/tmp` is shared with the neighbouring ports.** Use unique suffixes for any scratch file.
