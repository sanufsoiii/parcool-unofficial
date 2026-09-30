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

## The block registry key (`setId`) and the recipe ingredient form (added by the orchestrator, after the port was built)

Two things were wrong in this tree that the build could not see. Both are runtime-only, and both
are now fixed. The evidence below is `javap` against the 1.21.4 mojmap jar used everywhere else in
this file — `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-b238c5de88/…jar`
(the plain remapped jar, **not** the `-sources` jar, which is generated with the access widener
already applied and therefore lies about visibility).

### 1. `Blocks.java` built both hooks without `setId` — `NullPointerException` during registry fill

`Item`/`Block` must know their own registry key from 1.21.2 on. `javap` on the 1.21.4 jar:

```
net.minecraft.world.level.block.state.BlockBehaviour$Properties
  private net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> id;
  ...
  public BlockBehaviour$Properties setId(ResourceKey<Block>);        // javap line 79
  protected Optional<ResourceKey<LootTable>> effectiveDrops();        // javap line 58
  protected String effectiveDescriptionId();                         // javap line 81
```

`javap -c` on the same class, `effectiveDrops()`:

```
5:  getfield      #151   // Field drops:Lnet/minecraft/resources/DependantName;
8:  ldc_w         #345   // String Block id not set
11: invokestatic  #351   // Method java/util/Objects.requireNonNull:(…)
14: checkcast     #353   // class net/minecraft/resources/ResourceKey
17: invokeinterface #355 // DependantName.get(ResourceKey)
```

`effectiveDescriptionId()` is byte-for-byte the same three lines with the same constant. And
`BlockBehaviour`'s constructor calls **both** of them while the block is being constructed:

```
net.minecraft.world.level.block.state.BlockBehaviour(Properties)
  14: invokevirtual #102  // Properties.effectiveDrops:()Ljava/util/Optional;
  22: invokevirtual #108  // Properties.effectiveDescriptionId:()Ljava/lang/String;
```

So the throw does not wait for the game to ask for a description or a loot table: it happens while
`new WoodenZiplineHookBlock(...)` runs, i.e. while Architectury's `DeferredRegister` is filling
`Registries.BLOCK`. The same `requireNonNull(id, "Item id not set")` pair is in
`Item$Properties#effectiveDescriptionId()` (javap line 273) and `#effectiveModel()` (javap line 286),
and `Item`'s constructor calls them — which is why `Items.java` already needed `setId` and already
had it.

Architectury's `DeferredRegister` does not set it. Verified by scanning every class under
`dev/architectury/registry` and `dev/architectury/impl` in `architectury-fabric-16.1.4.jar` with
`javap -p -c` and grepping for `setId`: **zero** hits. NeoForge's own `DeferredRegister.Blocks` and
vanilla's `Blocks.register` both set it, which is why the omission is invisible in a
NeoForge-patched classpath and fatal under Architectury.

**Fix** (`common/src/main/java/com/alrex/parcool/common/block/Blocks.java`): each hook's
`BlockBehaviour.Properties.of()` is now followed by
`.setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, name)))`,
through a small `key(String)` helper, with a comment saying why — without it the next reader deletes
it as redundant and the mod crashes at init. The 1.21.6 and 1.21.7 trees have the same call and the
same reason; this port's copy of the comment drops the version number so it stays true if the file
is copied upward.

**Warning for the next port.** `setId` is mandatory on the whole 1.21.2+ branch, not just on 1.21.4
and 1.21.5. The read-only 1.21.1 tree has no `setId` anywhere, which is correct *for 1.21.1* — but a
port that imports a 1.21.1 base and targets anything ≥ 1.21.2 inherits a crash from the base even
though the base itself is fine. Check `Blocks.java` **and** `Items.java` against the target jar,
never against the tree the code came from. (The 1.21.11 tree does have both; §4 above and
`Items.java`'s own comment already said so for items only.)

### 2. The four vanilla recipes used the 1.21.1 ingredient form, which does not parse on 1.21.4

`data/parcool/recipe/{iron_zipline_hook,wooden_zipline_hook,zipline_rope,reset_zipline_rope}.json`
used `{"item": "minecraft:iron_nugget"}` and `{"tag": "minecraft:logs"}`. The last section of this
file already said that form does not parse; it had not been applied to the files. Confirmed by
running the real `Ingredient.CODEC` from the 1.21.4 jar against a bootstrapped vanilla registry:

```
{"item":"minecraft:chain"}  -> FAIL  Failed to parse either. First: Not a string: …
"minecraft:chain"           -> OK
"#minecraft:logs"           -> (parses; tag missing only because the harness has no datapacks)
{"tag":"minecraft:logs"}    -> FAIL  Failed to parse either. First: Not a string: …
```

`Ingredient.CODEC` is `ExtraCodecs.nonEmptyHolderSet(HolderSetCodec.create(Registries.ITEM, Item.CODEC, false))`,
and `HolderSetCodec`'s constructor builds `registryAwareCodec = Codec.either(TagKey.hashedCodec, compactListCodec(elementCodec, listOf(elementCodec)))` — a string, a `#tag` string, or an array of those. There is no object branch. The same test run against the 1.21.1 jar inverts completely (`{"item":…}` OK, `"minecraft:chain"` FAIL) and against 1.21.2 and 1.21.5 behaves exactly like 1.21.4, so the change is the 1.21.1 → 1.21.2 boundary, not a 1.21.4/1.21.5 quirk. Vanilla's own 1.21.4 `data/minecraft/recipe/*.json` (1337 of them) contain **zero** occurrences of `"item":`.

Failure mode in game: the recipe manager logs `Parsing error loading recipe parcool:<id>` and drops
the file, so all four recipes are missing from the crafting table — no crash, just no zipline.

**Fix:** the four files were rewritten to the string form (`"minecraft:iron_nugget"`,
`"#minecraft:logs"`). Re-decoding the rewritten files with the real
`ShapedRecipe.Serializer` codec succeeds for every one of them (the `#minecraft:logs` entry resolves
as soon as a tag registry is bound, which the harness cannot do). `zipline_rope_dye.json` is
untouched — it is the mod's own `CustomRecipe` and has no ingredients.

`"category": "misc"` and `minecraft:chain` were left exactly as they were, per the previous section
and per the resources jar (`assets/minecraft/items/chain.json` exists, `iron_chain.json` does not).

### 3. What was checked and turned out to be already correct

* `Item.Properties#useBlockDescriptionPrefix()` exists exactly once in each of the 1.21.4 and 1.21.5
  jars and is applied on **both** `BlockItem` properties, in `Items.blockItemProperties(String)`,
  which only the two hooks use. Its body just swaps the `DependantName` for the
  `BLOCK_DESCRIPTION_ID` variant — with it the keys are `block.parcool.*`, and
  `assets/parcool/lang/en_us.json` has `block.parcool.wooden_zipline_hook` /
  `block.parcool.iron_zipline_hook` and **no** `item.parcool.*zipline_hook*` keys. Matches.
* `assets/parcool/items/*.json` (3 files) is the right folder from 1.21.4: the target resources jar
  carries 1385 entries under `assets/minecraft/items/`. All three files are in the built jars. The
  `models/item/*.json` files that sit beside them are ignored by the game and were left alone.
* `pack.mcmeta`: `version.json` inside `neoforge-21.4.158-minecraft-resources-aka-client-extra.jar`
  says `"pack_version": {"resource": 46, "data": 61}`, and the file says `pack_format: 46` with no
  `supported_formats`. 1.21.4's `PackMetadataSection` reads `supported_formats` through
  `Codec.lenientOptionalFieldOf`, so omitting it is valid, and 46 is exactly the resource format.
  Nothing to change.
* `restoreVanillaBindings` is present in `KeyBindings` and is still needed here: in both 1.21.4 and
  1.21.5 `KeyMapping.MAP` is `Map<InputConstants$Key, KeyMapping>` — one mapping per physical key,
  `javap` line 4 of `net.minecraft.client.KeyMapping` — so ParCool's 16 shared keys still evict
  vanilla's. The call from `KeyRecorder#onClientTick` is still there.

## 10. Published artifacts

Superseded by §13 — the hashes below are the ones published *before* the mixin fixes, the
Architectury downgrade and the network fix. Kept for the history, not as the current artifact.

```
0.1-mc1.21.4fabric-3.4.3.3.jar     sha256 e85246799a4ecf9abdccf9970fccfc4c8dddb05d39a3a93cc7e376e3c307f714
0.1-mc1.21.4neoforge-3.4.3.3.jar   sha256 cb7f18254649f5b843daa975bfd68a1d205b76efe4bd782817ba5895c465846f
```

copied to `/home/sanufsoii/ports/готовые порты/parcool/`.

## 11. Defect found by running the game: the payload was decoded in a released buffer

Found by launching a client, not by building. **Both loaders**:
`fabric/src/main/java/com/alrex/parcool/platform/FabricParCoolNetwork.java` and
`neoforge/src/main/java/com/alrex/parcool/platform/NeoForgeParCoolNetwork.java`.

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

and again every few seconds, indefinitely.

### Root cause

One line, identical on both loaders, at `lambda$register$0`:

```java
(buf, context) -> context.queue(() -> handler.accept(erased.decode((RegistryFriendlyByteBuf) buf), context))
```

`NetworkManager.registerReceiver` hands the lambda a **raw** buffer on the network thread and
releases it as soon as the lambda returns. Not inferred from the log — it is what Architectury
15.0.3 (the version this port now pins) does in
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

The comment in the source restates the causal chain deliberately. Without it the next reader sees
"the decode is just a local read, inline it again" and reinstates the bug — the two forms are
identical in intent and differ only in statement order.

`javap -p -c` on the class inside both distributables shows `decode` at offset 2 and `queue` at
offset 22 of one synthetic method, plus a second synthetic method whose entire body is a single
`BiConsumer.accept` — the queued runnable no longer touches the buffer.

### This class of defect is invisible to the compiler and only a live client catches it

**`./gradlew build` is green on the broken code and stays green on the fixed code.** Nothing is wrong
to compile: `buf` is a live `RegistryFriendlyByteBuf` parameter, `decode` accepts it, capturing it
in a nested lambda is legal Java. The refcount is a *runtime* netty property and the validity window
is a *lifetime* property of Architectury's `registerReceiver` contract; neither is expressible in
the type system, so javac has nothing to say.

Equally invisible to a test: **without a client that has joined a world, no ParCool packet ever
travels**, the receiver lambda is never invoked, and the released-buffer read never happens. This
port's whole verification story — `checkCommonLoaderIndependence`, `:common:build`, `./gradlew
build`, the mixin-target checks against the real jars, unzipping the distributables — passes just as
happily on code that dies on the first packet.

The only oracle that finds this class of defect is a client a human launched, entering a world, with
a server at the other end. Compilation, build output, jar contents and mixin validation are all
necessary and none is sufficient.

### What was NOT verified for this fix

**The game was not launched.** No `:fabric:runClient`, no `:neoforge:runClient`, no server. The
evidence is Architectury's own source for the buffer contract, `javap` on the built classes, and a
green build. "The exception is gone" and "actions can now start" are *expected* from that evidence,
not observed.

## 12. The two mixin defects, and the Architectury version that goes with them

Carried over from the interrupted agent's working tree, verified here against the real 1.21.4 jar
before being accepted and committed.

### 12.1 `KeyboardInputMixin` — wrong descriptor, hard boot failure

`common/src/main/java/com/alrex/parcool/mixin/client/KeyboardInputMixin.java` inherited the 1.21.1
shape:

```java
@Inject(method = "tick", at = @At("RETURN"))
private void parcool$recordKeys(boolean slowDown, float movingSpeed, CallbackInfo ci)
```

`KeyboardInput#tick` has **no parameters** on 1.21.4. Verified with
`javap -p net.minecraft.client.player.KeyboardInput` against the Loom-provisioned
`minecraft-merged-1.21.4-*.jar`:

| version | `KeyboardInput#tick` |
|---|---|
| 1.21.2 | `public void tick(boolean, float)` |
| 1.21.3 | `public void tick(boolean, float)` |
| **1.21.4** | **`public void tick()`** |
| 1.21.5+ | `public void tick()` |

So on 1.21.4 the handler must take the `CallbackInfo` and nothing else, and the target is written
as the explicit `tick()V`:

```
Mixin apply for mod parcool failed parcool-common.mixins.json:client.KeyboardInputMixin from mod
  parcool -> net.minecraft.client.player.KeyboardInput: InvalidInjectionException: Invalid
  descriptor on ...->@Inject::parcool$recordKeys(ZFLorg/spongepowered/asm/mixin/injection/
  callback/CallbackInfo;)V! Expected (Lorg/spongepowered/asm/mixin/injection/callback/
  CallbackInfo;)V but found (ZFLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V
```

Note the class in the trace: `KeyboardInputMixin`, and the expected/found pair is exactly the
parameter-list difference above. The port's 1.21.2 and 1.21.3 siblings keep
`tick(boolean, float)` and their handlers are correct **there** — the shape is version-specific, so
do not copy one port's mixin into another.

### 12.2 `LivingEntityFallMixin` — redirect on a method `causeFallDamage` never calls

`@Redirect` targeted `LivingEntity.hurtOrSimulate(DamageSource, float)Z`, but on 1.21.4
`causeFallDamage` does not call it. `javap -c` on the 1.21.4 jar, inside
`public boolean causeFallDamage(float, float, DamageSource)`:

```
  38: aload_0
  39: aload_3        // the DamageSource
  40: iload         5 // calculateFallDamage result
  42: i2f
  43: invokevirtual #944  // Method hurt:(Lnet/minecraft/world/damagesource/DamageSource;F)V
  46: iconst_1
  47: ireturn
```

`javap -p` on `Entity` confirms `hurtOrSimulate` **does exist** on 1.21.4
(`public final boolean hurtOrSimulate(DamageSource, float)`) next to
`public final void hurt(DamageSource, float)` — so the class and the method were found, and only
the *call* was absent. A redirect with zero matching invocations is a validation failure, not a
silent no-op, and with `defaultRequire = 1` it aborts the boot:

```
InvalidInjectionException: Injection validation failed: Redirector parcool$applyDamageMultiplier(...)Z
  ... expected 1 invocation(s) but 0 succeeded. Scanned 0 target(s).
```

Redirecting the callee also fixes the return type: the target is `void`, so the handler is `void`.
1.21.2 and 1.21.3 already redirect `hurt(...)V` and their handlers return `void` — 1.21.4 simply
had the 1.21.1 target string left in it.

### 12.3 Why Architectury went from 16.1.4 to 15.0.3

`gradle.properties` now pins `architectury_api_version=15.0.3`, and
`fabric.mod.json` / `neoforge.mods.toml` lower their Architectury floors from `>=16.0.0` to
`>=15.0.3` to match.

**The hard dependency chain.** `architectury-fabric`'s pom declares fabric-api as a plain
`runtime`-scope (i.e. hard, non-optional) dependency:

| architectury-fabric | fabric-api it asks for | that fabric-api's own `depends.minecraft` |
|---|---|---|
| 15.0.3 | `0.110.5+1.21.4` | `>=1.21.4- <1.21.5-` |
| 16.0.1 | `0.119.0+1.21.5` | `>=1.21.5- <1.21.6-` |
| 16.0.3 | `0.119.5+1.21.5` | `>=1.21.5- <1.21.6-` |
| 16.1.4 | `0.119.5+1.21.5` | `>=1.21.5- <1.21.6-` |

This port declares `fabric_api_version=0.119.4+1.21.4`. Gradle resolves the conflict with
"highest version wins", and version ordering across the `0.119.x+1.21.4` / `0.119.x+1.21.5` split
is by the numeric `0.119.x` part, so with 16.1.4 the pulled `0.119.5+1.21.5` **beats** the port's
own `0.119.4+1.21.4` and silently replaces it — on a port whose target is 1.21.4. Fabric Loader then
refuses to boot:

```
[FabricLoader/Resolution] Immediate reason: [HARD_DEP architectury 16.1.4
  {depends fabric-api @ [>=0.100.0]},
  HARD_DEP_INCOMPATIBLE_PRESELECTED fabric-api 0.119.5+1.21.5
  {depends minecraft @ [>=1.21.5- <1.21.6-)}, ROOT_FORCELOAD_SINGLE architectury 16.1.4]
[FabricLoader/ERROR] Incompatible mods found!
```

**15.0.3 flips the comparison.** Its `0.110.5+1.21.4` is *below* the port's own `0.119.4+1.21.4`,
so the port's fabric-api wins on its own merits, and that jar's own `depends.minecraft` is
`>=1.21.4- <1.21.5-` — which matches this port's target exactly. The substitution that caused the
defect cannot occur, so no `exclude`, no `resolutionStrategy.force` and no
`{ force = true }` is needed anywhere. Verified by reading the published poms and the
`fabric.mod.json` inside the published fabric-api jars, not by reading a version table.

**16.x is a 1.21.5 build wearing a `~1.21.4-` label.** The version predicate alone cannot tell the
candidates apart, because 15.0.3, 16.0.1, 16.0.2, 16.0.3 and 16.1.4 *all* declare
`depends.minecraft = "~1.21.4-"` in their `fabric.mod.json`. What separates them is the last
parameter of `FarmBlock#fallOn` in the refmap target of `architectury.mixins.json`'s `MixinFarmBlock`.
`javap` on the 1.21.4 dev jar gives `public void fallOn(Level, BlockState, BlockPos, Entity, float)`
— a `float` — while 16.x's refmap asks for the post-1.21.4 signature with a `double`, and javap on
the 1.21.7/1.21.9 jars confirms 16.x's expectation there. So even with the fabric-api substitution
defended against, 16.x hard-crashes in Architectury's **own** mixin before the window exists:

```
Mixin apply for mod architectury failed architectury.mixins.json:MixinFarmBlock from mod
  architectury -> net.minecraft.world.level.block.FarmBlock: InvalidInjectionException:
  Injection validation failed: @Inject annotation on fallOn could not find any targets
  matching '.../FarmBlock;method_9554(...;Entity;D)V' in net.minecraft.world.level.block.FarmBlock.
#@!@# Game crashed! Crash report saved to: .../run/crash-reports/crash-...-client.txt
```

No `exclude` on ParCool's dependency line can reach a mixin inside Architectury's own jar, which is
why the fix is the version choice rather than a resolution rule. `fabric/build.gradle` carries the
full argument as a comment, including why the mechanical alternatives were rejected
(`AbstractExternalModuleDependency` has no `setForce(...)` on Gradle 9 — the Groovy `force = true`
form was removed — and `configurations.matching { ... }` has to name Loom's internal, non-API
configurations, which is a moving target on a tree already forced through a Loom migration from
1.7.435 to 1.17.493).

**The NeoForge half of the downgrade costs nothing.** `architectury-neoforge:15.0.3` exists on
`maven.architectury.dev` and its `neoforge.mods.toml` requires `minecraft [1.21.4,)` /
`neoforge [21.0.110-beta,)` — this port pins NeoForge `21.4.158`, which is inside that range. The
build resolving and compiling against it is the proof that the API surface ParCool uses is
unaffected: `:neoforge:compileJava` and the whole `./gradlew build` pass on 15.0.3 with no
architectury-symbol errors.

### 12.4 What was NOT verified for the mixin and Architectury work

**The game was not launched for any of this.** No `:fabric:runClient`, no `:neoforge:runClient`, no
server. The evidence for the two mixins is `javap` on the 1.21.4 dev jar and the published
Architectury/fabric-api poms and `fabric.mod.json`s. That the two `InvalidInjectionException`
boot aborts are gone is *expected* from that evidence, not observed — a mixin is validated against
the real runtime class, and only a launch proves the whole set applies.

## 13. Published artifacts (current)

```
0.1-mc1.21.4fabric-3.4.3.3.jar     sha256 95af859e742cda42812895890156ed1da6b29d5227b0655d79b5d8acceff1114
0.1-mc1.21.4neoforge-3.4.3.3.jar   sha256 2c4612113c28332033f6acbac057d49605e08f14bf9fbc75ab23db73f93cc35d
```

copied to `/home/sanufsoii/ports/готовые порты/parcool/`.

Both hashes necessarily differ from §10: this build carries the two mixin fixes, the
`architectury_api_version` 16.1.4 -> 15.0.3 downgrade (which also moves the `architectury` floor in
`fabric.mod.json` and `neoforge.mods.toml`), and the network decode fix on both loaders. The game
was not launched to validate any of it.
