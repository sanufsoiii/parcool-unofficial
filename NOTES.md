# NOTES — ParCool! 3.4.3.3 → Minecraft 1.21.4 (Architectury API)

Working notes for this port. Everything that had to be decided by looking at real data rather than
by memory is recorded here, together with the dead ends, the wrong statements in the brief, and the
traps for the next port.

## 0. Layout / references

* Base (imported, then retargeted): `parcool-Architectury-API-1.21.11` — read-only.
* Worked example of a finished port: `parcool-Architectury-API-1.21.1` — read-only.
* This tree: `parcool-Architectury-API-1.21.4`.

Neither reference tree was modified. They were read with `git` / `diff` / `javap` only.

## 1. Resolved toolchain — where each number came from

| Property | Value | Source of the number |
|---|---|---|
| `minecraft_version` | `1.21.4` | the task. Verified genuine: the Loom-provisioned `version.json` says `protocol_version 769`, `build_time 2024-12-03`, `pack_version {resource 46, data 61}` |
| `neo_version` | `21.4.158` | `maven-metadata.xml` of `net.neoforged:neoforge`; the 21.4 line has 158 releases, this is the newest |
| `architectury_api_version` | `16.1.4` | `architectury-fabric-16.1.4.jar` → `fabric.mod.json` `depends.minecraft = "~1.21.4-"`; `architectury-neoforge-16.1.4.jar` → `neoforge.mods.toml` `minecraft [1.21.4,)` / `neoforge [21.0.110-beta,)`. 15.0.3 declares the same line; 17.x is already `~1.21.7` |
| `loader_version` | `0.16.14` | `https://meta.fabricmc.net/v2/versions/loader/1.21.4`; newest 0.16 line, i.e. contemporary with 1.21.4. architectury-fabric 16.1.4 asks for `fabricloader >= 0.15.11` |
| `fabric_api_version` | `0.119.4+1.21.4` | Modrinth API, `fabric-api` filtered on game version 1.21.4 — newest published |
| `dev.architectury.loom` | `1.17.493` | **forced, not chosen** — see §2 |
| `architectury-plugin` | `3.5.170` | newest published (`https://maven.architectury.dev/architectury-plugin/architectury-plugin.gradle.plugin/maven-metadata.xml`, 150 versions) |
| `net.neoforged.moddev` | `1.0.9` | the 1.0.x line is the NeoForge 21.1–21.4 line; 2.x is 21.5+ |
| Gradle wrapper | `9.4.1` | forced by Loom 1.17 — see §2 |
| Java toolchain | 21 | both reference ports |
| `org.gradle.jvmargs` | `-Xmx2G` | **changed from the reference ports' `-Xmx3G`**: three ports build side by side on this 31 GB box and three 3 GB daemons do not fit. Do not "restore" this without checking how many daemons are running. |

### Launcher JDK

The machine's default `java` is **25**, which Gradle 8.10.2 does not support (max 23) and which
Gradle 9.4.1 only barely supports. Every Gradle invocation in this port therefore ran with
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`; the sibling 1.21.1 and 1.21.11 ports run their daemons on
the same JDK. This is an environment detail, not a build setting: there is deliberately **no**
`org.gradle.java.home` in `gradle.properties` (machine-specific path, forbidden by the definition of
done). The build itself only asks for toolchain 21.

## 2. Toolchain dead ends — the two that cost the most time

**Architectury Loom 1.7.435 (what the 1.21.1 port uses) cannot be used here.** The first
`./gradlew build` died with

```
A problem occurred configuring project ':fabric'.
> Failed to setup Minecraft, java.lang.IllegalStateException:
  Mod was built with a newer version of Loom (1.10.1), you are using Loom (1.7.435)
```

`architectury-fabric 16.1.4` was built with Loom 1.10.1, and Loom refuses to set up a mod built with
a newer Loom than its own. The brief's advice ("a newer Loom is usually fine and preferred") is
right, but the minimum is dictated by the Architectury artifact, not by the Minecraft version.

**Loom 1.10.455 (the line contemporary with 1.21.4) is *not* usable either**, because it is newer than
architectury-plugin:

```
Could not create task ':common:transformProductionFabric'.
  > 'boolean net.fabricmc.loom.LoomGradleExtension.disableObfuscation()'
```

`javap` over the cached `architectury-loom` jars shows `disableObfuscation()` exists **only** in
1.17.493, not in 1.7.435 and not in 1.10.455. Since architectury-plugin 3.5.170 is the newest version
published, **Loom 1.17.493 + Gradle 9.4.1 is the only combination that works at all** — which is why
this port uses the same Loom and Gradle as the 1.21.11 tree even though 1.21.11 is seven versions
away. Loom 1.17 does configure Minecraft 1.21.4 without complaint (verified: `./gradlew
:common:build` provisions the 1.21.4 merged jar and decompiles it).

**ModDevGradle 1.0.9 does not install its own repositories on Gradle 9.** `:neoforge` failed with

```
Could not resolve all artifacts for configuration ':neoforge:neoFormRuntimeDependenciesCompileClasspath'.
  > Could not find com.mojang:jtracy:1.0.29
```

and the "searched in" list contained only the five repositories written in `neoforge/build.gradle`.
ModDevGradle 1.0.9 has an internal `RepositoriesPlugin` that adds `https://libraries.minecraft.net/`
and `https://maven.neoforged.net/mojang-meta/`; on Gradle 8.10.2 it runs, on Gradle 9.4.1 it does not.
Both repositories are now written out explicitly in `neoforge/build.gradle` with that explanation.

## 3. The big structural decision: which tree is the base

The brief says "**Base = the 1.21.11 tree**", and also says to decide per file which reference is
closer to the target. Measured, the second rule decides almost everything:

* 1.21.1 sources against MC 1.21.4 → **99 compiler errors**
* 1.21.11 sources against MC 1.21.4 → **204 compiler errors**

So `common/src/main/java` was taken from the 1.21.1 tree and then moved forward file by file. The
result is the same tree the brief's rule 1 describes, reached by the other direction, and every
difference was re-derived against the 1.21.4 jar rather than assumed. The one place where the
count is misleading is worth writing down: **1.21.4 is much closer to 1.21.11 than the brief's
1.21.1/1.21.11 table suggests.** 1.21.4 already has `EntityRenderer<T, S>` render states,
`EntityRenderState`, `ItemTintSource`/`ItemTintSources`, `GuiGraphics#blit(RenderType, …)`,
`ClientInput`, `Entity#hurtServer`, `BlockEntityType` without a builder, `Item.Properties#setId`,
item model definitions, the `InteractionResult` interface, `KeyEvent`/`MouseButtonEvent` input
plumbing, and the `BlockBehaviour#updateShape` rewrite. The brief's table draws the line at
1.21.1 vs 1.21.11; for 1.21.4 the line falls in the middle, and it is not where the table puts it.

`StaminaHUDController` is the clearest single example of why the table cannot be trusted: it
implements `LayeredDraw.Layer`, which exists in **1.21.4** and was removed in 1.21.9. Taking the
1.21.11 side there would not compile.

## 4. Version deltas — every decision, with the evidence

"1.21.4 side" = the shape the 1.21.4 mojmap jar actually has (checked with `javap`).

| Area | 1.21.4 has | What this port uses |
|---|---|---|
| `ResourceLocation` vs `Identifier` | `net.minecraft.resources.ResourceLocation`, no `Identifier` | `ResourceLocation` |
| Attribute holder lookup | `Registry#get(ResourceKey)` → `Optional`; no `getHolder` | 1.21.11 side: `BuiltInRegistries.ATTRIBUTE.get(key).orElseThrow(...)` |
| Entity / BlockEntity save | `CompoundTag`; no `ValueInput`/`ValueOutput` | 1.21.1 side |
| `BlockEntityType` | `Builder` gone, `register(String, …)` private, ctor private | 1.21.11 side: `mixin.common.BlockEntityTypeInvoker` + `ParCoolPlatform#registerBlockEntityType` |
| Render types | `RenderType extends RenderStateShard`; no `RenderSetup`/`RenderPipeline` | 1.21.1 side, plus an access widening (below) |
| Entity rendering | `EntityRenderer<T extends Entity, S extends EntityRenderState>` with `extractRenderState` + `render(S, PoseStack, MultiBufferSource, int)`; **no** `submit(…)`/`SubmitNodeCollector` | hybrid: render state, but `render(…)` — i.e. `ZiplineRopeRenderer`, `PlayerModelMixin`, `PlayerRendererMixin` are all 1.21.11-shaped with 1.21.4 names |
| `PlayerRenderer` / `PlayerModel` | `net.minecraft.client.renderer.entity.player.PlayerRenderer`, `net.minecraft.client.model.PlayerModel` (no type parameter, extends `HumanoidModel<PlayerRenderState>`); no `AvatarRenderer` | 1.21.11 shape with `PlayerRenderState` / `PlayerModel` and `com.alrex.parcool.compat.IPlayerRenderStateEntity` |
| Key mappings | `KeyMapping` has a `String` category; `KeyMapping.MAP` is `Map<Key, KeyMapping>` — **one mapping per physical key** | 1.21.1 side: `KeyBindings#restoreVanillaBindings` **is** ported, and `KeyRecorder#onClientTick` still calls it |
| Recipe ingredients | object form `{"item": "minecraft:chain"}` (the string form arrives in 1.21.5) | 1.21.1 side |
| `pack.mcmeta` | resource format **46**, data 61 | `pack_format: 46` |
| `Entity#isInWaterOrBubble` | present | 1.21.1 side: called directly, no `EntityUtil` re-implementation |
| `Player#canInteractWithEntity` | present | 1.21.1 side |
| `jumpFromGround` | moved to `LivingEntity`; `Player` no longer declares it | 1.21.11 side: `mixin.common.LivingEntityJumpMixin`; the two injects were removed from `PlayerMixin` |
| `Item` description id | `final String descriptionId` field set at construction; `BlockItem` no longer overrides `getDescriptionId`; the key must be set or the ctor throws `NullPointerException("Item id not set")` | 1.21.11 side: `Item.Properties#setId(...)` **plus** `.useBlockDescriptionPrefix()` so the hook names stay `block.parcool.*` |
| Item models | `assets/<ns>/items/<id>.json` item model definitions **exist** (1385 of them ship with 1.21.4) | 1.21.11 side: `assets/parcool/items/*.json` are present |
| Item tints | `ItemTintSource` + `ItemTintSources` codec registry exist; Architectury 16 dropped `ColorHandlerRegistry.registerItemColors` | 1.21.11 side: `ItemTintSourcesAccessor` |
| `ClientInput` | `LocalPlayer#input` is a `ClientInput`; key state lives in the `Input` record (`keyPresses.jump()`, `.forward()`, …) | 1.21.1 side's structure with the `keyPresses` accessors |
| `GuiGraphics#blit` | no overload without a render-type function | 1.21.11 shape: `blit(RenderType::guiTextured, …, -1)` |
| `ParticleEngine#destroy` | present | 1.21.1 side |
| `Level` ctor | lost the `Supplier<ProfilerFiller>` argument | 1.21.11 side |
| `LivingEntity#hurt` | `final void`; `causeFallDamage(float, float, DamageSource)` still returns `boolean` and calls `hurtOrSimulate` | 1.21.1 signature, redirect retargeted to `hurtOrSimulate` |
| `CustomRecipe` | no `canCraftInDimensions`; `getSerializer()` returns `RecipeSerializer<? extends CustomRecipe>`; the serializer is `CustomRecipe.Serializer` | 1.21.11 side |
| `Potion` | `Potion(String name, MobEffectInstance…)`; `PotionBrewing.Builder#addMix(Holder<Potion>, Item, Holder<Potion>)` | 1.21.11 side |
| `EntityType.Builder#build` | needs a `ResourceKey` | 1.21.11 side |
| `BlockBehaviour#updateShape` | 8-argument form with `ScheduledTickAccess` | 1.21.11 side |
| `Minecraft#getTimer()` | `getDeltaTracker()` | 1.21.11 side |
| `WallBlock.NORTH_WALL` | still `NORTH_WALL` (renamed to `NORTH` only in 1.21.5) | 1.21.1 side |
| `Entity#getCommandSenderWorld()` | still present | 1.21.1 side |
| `LayeredDraw.Layer` / `GuiLayer` | exists | 1.21.1 side |
| NeoForge mapping naming | mojmap (same reasoning as the 1.21.11 port) | mojmap in the AT |

### The one access widening this version needs

`client/renderer/RenderTypes` builds the rope's two render types out of `RenderStateShard`, and
**1.21.4 tightened both ends**: `RenderType.create(String, VertexFormat, Mode, int, boolean,
boolean, CompositeState)` became private (1.21.1 still had a package-private five-argument overload
that did the same job) and the `RenderStateShard` shards are `protected`. The reference 1.21.1 tree
compiles that file without a widener, which cannot be true against vanilla 1.21.1 — see §5, item 1.
Here the five fields and the one method are widened in `parcool.accesswidener` (Fabric) and in
`META-INF/accesstransformer.cfg` (NeoForge), and `./gradlew :common:validateAccessWidener` passes.

## 5. Bugs found in the reference ports (not fixed there)

1. **The 1.21.1 tree's `RenderTypes.java` cannot compile against vanilla 1.21.1.** It calls the
   private seven-argument `RenderType.create` and reads the `protected` `RenderStateShard` shards
   with no widener and no access transformer for either (its `accesstransformer.cfg` does list the
   five shards, but with **SRG** names `f_110147_` etc., and it lists no `create`). Yet
   `common/build/classes/.../RenderTypes.class` exists in that tree, so it was compiled against
   something that had those members open — most likely a NeoForge-patched classpath leaking into
   `:common`. **Do not copy that file verbatim into another tree**; widen it properly, as done here.
2. **The 1.21.11 tree's `BufferUtil` is a regression.** `ensureRoom` was made `static` and reads a
   `static ByteBuffer current` that is only ever assigned in the constructor, so the bounds message
   is meaningless — and, worse, the `ensureRoom` calls were **deleted** from `putVector3i` and
   `putVec3`, so those two writers no longer check at all. The 1.21.1 version is correct and is what
   this port uses.
3. **The 1.21.11 tree's `ConfigSpec#persist()` never persists anything.** It was changed to only set
   `dirty = true`, but no caller ever reads `isDirty()` or calls `save()` afterwards, so a change made
   in the settings screen is lost on exit. The 1.21.1 version (write the file) is used here. The
   underlying complaint is real — `persist()` runs once per rendered frame while a slider is dragged,
   so it rewrites the whole document hundreds of times — but the 1.21.11 "fix" trades that for data
   loss, which is worse. A debounce would be the right fix; that is a redesign, not a port.
4. **The 1.21.11 `neoforge.mods.toml` lists `betterthirdperson` as `[1.9.0,)` and `paraglider` as
   `[21.11.0-beta.1,)`**; those ranges are of course per-version here (`[21.4.0,)`,
   `[1.21.4-5.0.0,)`). Just be aware the ranges were never actually validated against anything.

## 6. Decisions taken that differ from one reference port on purpose

* **Base = 1.21.1 sources** (§3). Recorded here because it contradicts the brief's letter; the
  outcome is what the brief intends.
* **`Animation#setAnimator(Class<? extends Animator>, Object…)` was ported** (1.21.11 shape, 51 call
  sites converted). The reasoning is a dedicated-server failure: the `Action` implementations live in
  `:common`, are instantiated by `Parkourability` on a server, and a `new ChargeJumpAnimator()`
  there makes the JVM verifier load the whole client-only animation package, ending in
  "Attempted to load class net.minecraft.client.player.LocalPlayer which is not present on the
  dedicated server". `Class#getConstructors()` needs public constructors and every animator involved
  has one (checked). **Note that the same fix is needed for signatures, not just for `new`:** the JVM
  verifier resolves parameter types when it checks that a subclass overrides a method, which is why
  `Action#wantsToShowStatusBar` / `getStatusValue` were widened from `LocalPlayer` to `Player` here
  too (1.21.11 shape). Local `checkcast`/`instanceof` inside a method body are *not* resolved at
  verification time and were left alone.
* **ModDevGradle's repositories are written out explicitly** (§2).
* **`org.gradle.jvmargs=-Xmx2G`** instead of `-Xmx3G` (§1).

## 7. Upstream ParCool bugs

None found that the 1.21.11 tree had already fixed; the two the brief names are both present here
and were verified by reading the code:

* `ZiplineRopeEntity#addAdditionalSaveData` writes six distinct keys (`Tile1_X/Y/Z`, `Tile2_X/Y/Z`).
* `KeyBindings#isDown` / `isMetaKeyDown` reject an unbound keysym before `glfwGetKey`
  (`isPollableKeysym`), and `KeyBindings#restoreVanillaBindings` is present.

## 8. What the next port must not trust

1. **Do not trust the brief's "1.21.1 (old side) vs 1.21.11 (new side)" column assignment.** Two
   rows of its table are outright wrong for 1.21.4 and one is reversed (recipe ingredients: the
   object form is 1.21.1 *and* 1.21.4, the string form only arrives in 1.21.5). The entity-rendering
   row is also misleading: 1.21.4 has the render state but not `submit`/`SubmitNodeCollector`.
   PROMPT.md was corrected in this folder; check the jar, not the table.
2. **Do not assume the 1.21.11 tree is the "finished" one.** It carries the animator
   deduplication, the `Animation`/`Action` dedicated-server fixes, the `copyFromBodyToWear` fix and
   the unified Architectury networking — all of which are version-independent and were ported. It
   also carries the two regressions in §5. Neither tree is safe to copy wholesale; diff, then decide.
3. **Architectury Loom's usable version is set by `architectury-plugin`, not by Minecraft.** The
   chain is: newest architectury-plugin → the Loom that still has `disableObfuscation()` → that
   Loom's minimum Gradle. For `architectury-plugin 3.5.170` that is Loom 1.17.x on Gradle 8.11+,
   whatever Minecraft version you are targeting. Re-derive it, do not copy 1.7.435.
4. **ModDevGradle 1.0.x's repository injection silently does nothing on Gradle 9.** Write
   `https://libraries.minecraft.net/` and `https://maven.neoforged.net/mojang-meta/` out.
5. **`curse.maven` coordinates must be mojmap builds.** A `compileOnly` Maven dependency is not
   remapped by Loom, so the Fabric jar of a mod cannot be used to compile against: the
   `ShoulderSurfing-Fabric-1.21.4-4.12.0` interface says `isForcingCameraCoupling(class_310)` and
   fails to compile, while the NeoForge build of the same version is fine.
6. **The CurseForge listing reachable from this machine is truncated.** `api/v1/mods/<id>/files`
   only exposes the newest ~50–90 files and ignores `page`/`gameVersion`; there is no
   `pageIndex` past the first page for some projects. Paraglider and EpicFight have no 1.21.4 build
   in what is reachable, so both are compiled against their 1.21.1 build (`7795089` / `8175609`);
   BetterThirdPerson (`5965876`) and ShoulderSurfing (`6543497`/`6543501`) *are* 1.21.4 builds.
7. **A fresh checkout needs two Gradle invocations**, not one: `./gradlew :common:build` and then
   `./gradlew build`. Architectury Loom resolves `:common`'s jar while it *configures* `:fabric`, and
   the root `build` → `bootstrap` dependency is only evaluated after configuration. This is
   inherited from the 1.21.1 and 1.21.11 trees; it is documented in `BUILDING.md` rather than fixed.
8. **Two Gradle daemons from sibling ports pin Loom 1.7.435 and Loom 1.17.493 in the shared Gradle
   home at the same time.** They do not conflict, but do not run `./gradlew --stop` to "fix" a Loom
   problem — that kills the other ports' daemons. Delete `.gradle/loom-cache` and `build/`
   directories instead.

## 9. How this port was verified

`./gradlew build` from a cleaned tree (`build/` and `.gradle/` deleted) succeeds, in two steps as
described in §8.7. Checked in the produced jars:

* Fabric jar: 29 mixin classes, `parcool.accesswidener` in `v2 intermediary`, **no refmap**,
  mixin targets statically remapped into the bytecode (`@Mixin(class_591)`,
  `setupAnim(class_10055)`, `slim` → `field_3480`), `fabric.mod.json` with
  `"version": "1.21.4-3.4.3.3"`, the `META-INF/services` entry pointing at
  `FabricParCoolPlatform`, `assets/parcool/items/*.json` present, all five recipe files present.
* NeoForge jar: 29 mixin classes, **mojmap** bytecode (`net/minecraft/client/model/PlayerModel`,
  `net/minecraft/client/renderer/entity/state/PlayerRenderState`), `META-INF/neoforge.mods.toml`
  with `versionRange = "[21.4.0,)"` / `"[1.21.4,1.22)"` / architectury `"[16.0.0,)"`, the access
  transformer and the `LICENSE` it points at, the `META-INF/services` entry pointing at
  `NeoForgeParCoolPlatform`.
* `:common:checkCommonLoaderIndependence` passes (it is wired into `:common:check`).
* No `System.out`, no `printStackTrace`, no `TODO`, no absolute path anywhere in the sources or the
  build scripts, no unused imports (checked with a script over every `import` in all three modules).

**Not verified: the game.** No client, no server, no Prism instance was started — running Minecraft
was explicitly out of scope for this task. That means the mixin set has been verified *statically*
(every `@Mixin` target, every `@Inject`/`@Redirect`/`@Shadow`/`@Accessor`/`@Invoker` name and
descriptor was checked against the 1.21.4 mojmap jar with `javap`) but **not** at runtime, and the
play-through checklist from the brief's phase 6 (name tags, stamina HUD, wall run, zipline ride,
cross-loader join, vanilla keys still working next to ParCool's) has not been performed. Treat a first
`:fabric:runClient` as the remaining step before shipping these jars to a player.

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
