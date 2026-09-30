# NOTES — ParCool! 3.4.3.3 → Minecraft 1.21.5 (Architectury API)

Working notes for this port. Everything that had to be decided by looking at real data rather than
by memory is recorded here, together with the dead ends, the wrong statements in the brief, and the
traps for the next port.

## 0. Layout / references

* Imported base: `parcool-Architectury-API-1.21.4` — read-only, the finished port of the same mod to
  the version immediately before this one. The brief names `parcool-Architectury-API-1.21.11` as the
  base; see §3 for why that was not the right starting point for 1.21.5 and what it produced
  instead.
* Secondary reference: `parcool-Architectury-API-1.21.11` — read-only, used as the source of the
  fixes for the API shapes that 1.21.5 shares with 1.21.11.
* Worked example of a finished multiloader port: `parcool-Architectury-API-1.21.1` — read-only.
* This tree: `parcool-Architectury-API-1.21.5`.

No reference tree was modified. They were read with `git` / `diff` / `javap` only.

## 1. Resolved toolchain — where each number came from

| Property | Value | Source of the number |
|---|---|---|
| `minecraft_version` | `1.21.5` | the task. Verified genuine: the `version.json` inside `minecraft-server-1.21.5.jar` says `protocol_version 770`, `pack_version {resource 55, data 71}`, `releaseTime 2025-03-25T12:14:58+00:00` |
| `neo_version` | `21.5.98` | `maven-metadata.xml` of `net.neoforged:neoforge`; the 21.5 line ends at 21.5.98, 21.6.x is already out and targets MC 1.21.6+ |
| `architectury_api_version` | `16.1.4` | `architectury-fabric-16.1.4.jar` → `fabric.mod.json` `depends.minecraft = "~1.21.4-"` (semver, so `>=1.21.4 <1.22.0`) and `fabricloader >= 0.15.11`; `architectury-neoforge-16.1.4.jar` → `neoforge.mods.toml` `minecraft [1.21.4,)` / `neoforge [21.0.110-beta,)`, which 21.5.98 satisfies. 16.x is the newest line that still accepts 1.21.5: `architectury-fabric 17.0.8` and `18.0.5` both declare `depends.minecraft = "~1.21.7"`, and 19.0.1 declares `~1.21.11` |
| `loader_version` | `0.16.14` | `https://meta.fabricmc.net/v2/versions/loader/1.21.5`; newest 0.16 line, i.e. contemporary with 1.21.5. `fabric-api 0.128.2+1.21.5` asks for `fabricloader >= 0.16.10`, and 0.16.14 is the newest release in the only loader line that both is contemporary and satisfies it (0.17–0.19 also support 1.21.5 but are two years newer than the game) |
| `fabric_api_version` | `0.128.2+1.21.5` | Modrinth API, `fabric-api` filtered on game version 1.21.5 — newest published. Its `fabric.mod.json` says `minecraft: ">=1.21.5- <1.21.6-"` |
| `dev.architectury.loom` | `1.17.493` | **forced, not chosen** — see §2 |
| `architectury-plugin` | `3.5.170` | newest published (`…/architectury-plugin.gradle.plugin/maven-metadata.xml`; the `<latest>` tag is a `3.5-SNAPSHOT`, the newest real release is 3.5.170) |
| `net.neoforged.moddev` | `1.0.24` | the 1.0.x line is the NeoForge 21.1 – 21.5 line; 1.0.24 is its newest release (2025-01-02), 2.0.x is the 21.6+ line |
| Gradle wrapper | `9.4.1` | forced by Loom 1.17 — see §2 |
| Java toolchain | 21 | the game's own `version.json` says `javaVersion.majorVersion = 21` |
| `org.gradle.jvmargs` | `-Xmx2G` | **not** the reference ports' `-Xmx3G`: three ports build side by side on this 31 GB box and three 3 GB daemons do not fit. Do not "restore" this without checking how many daemons are running |

### Launcher JDK

The machine's default `java` is **25**, which Gradle 9.4.1 only barely supports. Every Gradle
invocation in this port therefore ran with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`; the sibling
ports run their daemons on the same JDK. This is an environment detail, not a build setting: there is
deliberately **no** `org.gradle.java.home` in `gradle.properties` (a machine-specific path, forbidden
by the definition of done). The build itself only asks for toolchain 21.

### Where the 1.21.5 jar that every API check in this file was made against lives

```
~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/
  1.21.5-loom.mappings.1_21_5.layered+hash.40545-v2/
  minecraft-merged-1.21.5-loom.mappings.1_21_5.layered+hash.40545-v2.jar
```

`javap -p -cp <that jar> <class>` is the authority for every "has / has not" statement below.
The `minecraft-merged-*-sources.jar` variant of the same directory is **not** usable for visibility
questions: Loom generates it with the project's own access widener already applied, so it lies about
what is `private` in vanilla.

## 2. Toolchain — the two dead ends

**Architectury Loom cannot be chosen freely; `architectury-plugin` chooses it.**
`architectury-plugin` 3.5.170 calls `LoomGradleExtension#disableObfuscation()`, and `javap` over the
cached `architectury-loom` jars shows that method exists **only** in Loom 1.17.493 — not in 1.7.435
(the 1.21.1 port's choice) and not in 1.10.455 (the line contemporary with 1.21.4/1.21.5). With
Loom 1.7.435 the build stops earlier still, because `architectury-fabric 16.1.4` was built with
Loom 1.10.1 and Loom refuses to set up a mod built with a newer Loom than its own:
`Mod was built with a newer version of Loom (1.10.1), you are using Loom (1.7.435)`. So
**Loom 1.17.493 + Gradle 9.4.1 is the only combination that works at all**, whatever Minecraft
version is targeted. Loom 1.17 configures Minecraft 1.21.5 without complaint (verified: this build
provisions the 1.21.5 merged jar, remaps it and applies the access widener).

**ModDevGradle 1.0.x does not install its own repositories on Gradle 9.** ModDevGradle has an
internal `RepositoriesPlugin` that adds `https://libraries.minecraft.net/` and
`https://maven.neoforged.net/mojang-meta/`; on Gradle 8 it runs, on Gradle 9 it does not, and
`:neoforge` then fails to resolve `neoFormRuntimeDependenciesCompileClasspath` with
`Could not find com.mojang:jtracy`. Both repositories are therefore written out explicitly in
`neoforge/build.gradle`, with the explanation. (1.0.24 itself works fine against NeoForge 21.5.98:
it decompiles, applies the patches, applies the project's access transformer and recompiles.)

**`www.cursemaven.com` is dead.** Every file of every project answers `404` now — including the ones
the reference ports list in their `build.gradle` comments, which means those ports resolve purely out
of the shared Gradle module cache. A `curse.maven` coordinate that is not already cached therefore
cannot be resolved on this machine at all. §7 lists what that costs.

## 3. The big structural decision: which tree is the base

The brief says "**Base = the 1.21.11 tree**" and, in the same section, "before you rewrite anything,
read the corresponding file in the 1.21.1 port … the 1.21.1 port also contains fixes that the 1.21.11
port dropped". For 1.21.5 neither of those two is the right answer, and the choice is not a matter of
taste: the 1.21.4 tree is the finished port of the *immediately preceding* game version, and the
imported-1.21.4-sources tree compiled against 1.21.5 with **51 errors**, all of them in seven
well-defined places (§4). The same 1.21.11-sources tree has 204 errors against 1.21.4 and far more
against 1.21.5.

So: the tree was imported from `parcool-Architectury-API-1.21.4` (a finished port, clean working
tree, not one of the two the brief names as read-only), and the 1.21.11 tree was used as the source
for every API shape 1.21.5 shares with it. This contradicts the brief's letter and produces exactly
what the brief intends. It is recorded here because the rule the brief actually wants is "the port
closest to the target wins", not "1.21.11 wins".

**Neither reference tree is safe to copy wholesale.** The 1.21.4 tree carries two mixins that are
broken against *its own* target version and only work here because they happen to match 1.21.5's
shapes (§5.1, §5.2), and one class of stale comments (§9).

## 4. Version deltas — every decision, with the evidence

"1.21.5 side" = the shape the 1.21.5 mojmap jar actually has (checked with `javap`, and for anything
non-trivial with vineflower on the decompiled class).

| Area | 1.21.5 has | What this port does |
|---|---|---|
| `ResourceLocation` vs `Identifier` | `net.minecraft.resources.ResourceLocation`, no `Identifier` | `ResourceLocation` (1.21.1/1.21.4 side) |
| **NBT getters** | `getInt`/`getBoolean`/`getCompound`/… return `Optional<T>`; a `getXOr(key, default)` family was added next to them; `getAllKeys()` → `keySet()`; `contains(String, byte)` is gone | `getIntOr` / `getBooleanOr` / `getCompoundOrEmpty` / `keySet()` / `contains(String)` |
| **`BlockBehaviour#onRemove`** | **removed entirely** — `javap` finds no `onRemove` on `Block` nor on `BlockBehaviour`; `LevelChunk#setBlockState` now calls `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` on the outgoing block entity, server-side only | the zipline hook's drop-the-rope hook moved from `ZiplineHookBlock#onRemove` to `ZiplineHookTileEntity#preRemoveSideEffects` |
| **`ClientInput`** | `leftImpulse` / `forwardImpulse` fields are gone; `moveVector` is `protected`; `getMoveVector()` is the accessor | `input.getMoveVector().x` / `.y` (x = left−right, y = forward−backward, exactly the old `leftImpulse` / `forwardImpulse`) |
| **`LivingEntity#causeFallDamage`** | `(double, float, DamageSource)` — the fall distance widened from `float` in 1.21.4 | handler declared with the `double`, narrowed back to the `float` the ParCool event carries |
| **`Item#appendHoverText`** | `(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` | the 1.21.11 shape |
| **`WallBlock` side properties** | `NORTH` / `SOUTH` / `EAST` / `WEST`, the `_WALL` suffix is gone | the new names |
| **`Entity#isInWaterOrBubble`** | **removed** | re-implemented in `utilities.EntityUtil`, 19 call sites retargeted |
| **Recipe ingredients** | **string form**: `"minecraft:chain"`, `"#minecraft:logs"`, `["parcool:zipline_rope"]` | all four vanilla-shape recipes rewritten; the `result` object (`{"id": …, "count": …}`) is unchanged |
| **`RenderType`** | no `RenderStateShard` shader/cull shards any more (`RenderStateShard$CullStateShard` and `RENDERTYPE_LEASH_SHADER` are not in the class); the vertex format, primitive mode and culling live in a `RenderPipeline`; the factory is `create(String, int, RenderPipeline, CompositeState)`; `RenderPipelines#PIPELINES_BY_LOCATION` and `#MATRICES_COLOR_FOG_SNIPPET` are private | custom `RenderPipeline`s built from the very snippet `RenderPipelines.LEASH` uses, registered into the location map; four new access-widener / access-transformer entries |
| Attribute holder lookup | `Registry#get(ResourceKey)` → `Optional` | 1.21.11 side: `BuiltInRegistries.ATTRIBUTE.get(key).orElseThrow(…)` |
| Entity / BlockEntity save | `CompoundTag`; no `ValueInput` / `ValueOutput` | 1.21.1 side |
| `BlockEntityType` | `Builder` gone, `register(String, …)` private | 1.21.11 side: `mixin.common.BlockEntityTypeInvoker` + `ParCoolPlatform#registerBlockEntityType` |
| Entity rendering | `EntityRenderer<T extends Entity, S extends EntityRenderState>` with `extractRenderState` + `render(S, PoseStack, MultiBufferSource, int)`; **no** `submit(…)` / `SubmitNodeCollector` | 1.21.4 shape unchanged |
| `PlayerRenderer` / `PlayerModel` | `…entity.player.PlayerRenderer`, `net.minecraft.client.model.PlayerModel` (no type parameter); no `AvatarRenderer` | 1.21.4 shape unchanged |
| Key mappings | `KeyMapping` has a `String` category; `KeyMapping.MAP` is `Map<Key, KeyMapping>` — **one mapping per physical key**; `ALL` is `Map<String, KeyMapping>` | 1.21.1 side: `KeyBindings#restoreVanillaBindings` **is** ported, and `KeyRecorder#onClientTick` still drives it |
| `pack.mcmeta` | `PackMetadataSection` reads `pack_format` plus an optional `supported_formats` range. There is **no** `min_format` / `max_format` in 1.21.5 | `pack_format: 55`, `supported_formats: [55, 71]` (the resource and data pack formats of 1.21.5) |
| `LayeredDraw.Layer` | exists (removed only much later) | 1.21.1 side |
| `InteractionResult` | interface with `SUCCESS` / `SUCCESS_SERVER`; no `sidedSuccess` helper | the explicit two-branch form |
| NeoForge mapping naming | mojmap (same reasoning as the 1.21.11 port) | mojmap in the AT |

### Verified and therefore *not* changed

These are the things a port from 1.21.1 would get wrong, all confirmed against the 1.21.5 jar:
`Item.Properties#setId` + `useBlockDescriptionPrefix` and the item model definitions in
`assets/parcool/items/`, `ItemTintSource`/`ItemTintSources` (moved to
`net.minecraft.client.color.item`), `jumpFromGround` on `LivingEntity`, `Entity#hurt` `final void` +
`hurtOrSimulate`/`hurtServer`, `Level` without the `ProfilerFiller` ctor argument, `EntityType.Builder
#build(ResourceKey)`, `Potion(String, MobEffectInstance…)` + `PotionBrewing.Builder#addMix(Holder,…)`,
`BlockBehaviour#updateShape` in its 8-argument `ScheduledTickAccess` form, `GuiGraphics#blit` with a
render-type function, `CustomRecipe` without `canCraftInDimensions` and with an optional `category`,
`key.categories.*` still being the key-mapping category prefix, and the existence of every sound file
`sounds.json` names (`entity/leashknot/place1..3`, `mob/sheep/shear`, all three present in the 1.21.5
asset index).

## 5. Bugs found in the reference ports

**Fixed here** (both were already broken against 1.21.4, so the 1.21.4 tree could not have booted
either — they are inherited from the 1.21.1 tree, where both were correct):

1. **`KeyboardInputMixin` declared a handler with two arguments the target does not have.**
   `KeyboardInput#tick()` has taken no parameters for many versions; the handler was
   `tick(boolean slowDown, float movingSpeed, CallbackInfo)`. A handler whose arity does not match is
   an invalid injection signature, i.e. with `defaultRequire: 1` a hard boot failure. Now `tick()V`
   with a bare `CallbackInfo` — the 1.21.11 tree already had this.
2. **`LivingEntityFallMixin` redirected a call that does not exist.** `Entity#hurt` has been
   `final void` since 1.21.2 and `LivingEntity#causeFallDamage` calls `this.hurt(source, i)`; the
   redirect was aimed at `hurtOrSimulate(DamageSource;F)Z`, which appears nowhere in the method, so
   the damage-multiplier hook was a silent no-op. Now redirected at `hurt(DamageSource;F)V` with a
   `void` handler.

**Deliberately not copied from the 1.21.11 tree** (they would have regressed this port; both are the
1.21.11 fixes for API shapes 1.21.5 does not have):

3. **`BufferUtil`**: the 1.21.11 tree made `ensureRoom` `static` over a `static ByteBuffer current`
   that is only ever assigned in the constructor (a data race, and a meaningless bounds message) and
   **deleted the `ensureRoom` calls from `putVector3i` and `putVec3`**. The 1.21.1 version is used.
4. **`ConfigSpec#persist()`**: the 1.21.11 tree reduced it to `dirty = true`, which nobody ever
   reads, so every change made in the settings screen is lost on exit. The 1.21.1 version (write the
   file) is used. The underlying complaint — `persist()` runs once per rendered frame while a slider
   is dragged — is real, but the fix is a debounce, i.e. a redesign, not a port.

**Also verified present and correct in this tree** (the two the brief names): `ZiplineRopeEntity
#addAdditionalSaveData` writes six distinct NBT keys, and `KeyBindings#isDown` / `isMetaKeyDown`
reject an unbound keysym (`isPollableKeysym`) before `glfwGetKey`. `Limitations`' filename validation
and `Animation#setAnimator`'s dedicated-server-safe `Class`-literal form are inherited from the 1.21.4
tree and were re-checked, not assumed.

## 6. Upstream ParCool bugs

None found beyond the two the brief names, both already fixed in this tree (§5).

## 7. The optional integrations, and what could not be done

| Integration | Compiled against | Note |
|---|---|---|
| Paraglider | `curse.maven:paraglider-289240:7795089` (Paraglider-neoforge-21.1.5, MC 1.21.1) | upstream publishes `Paraglider-neoforge-21.5.2` (file 6739612) for MC 1.21.5, but cursemaven 404s it and it is not in the local cache, so it cannot be used here |
| EpicFight | `curse.maven:EpicFight-405076:8175609` (MC 1.21.1) | no 1.21.5 build on the reachable CurseForge pages; `EpicFightManager#isEpicFightUsable` makes the integration report itself absent at runtime |
| BetterThirdPerson | `curse.maven:BetterThirdPerson-435044:6455836` (NeoForge 1.21.5+, exact) | switched from the 1.21.4 build — this one was already in the shared Gradle cache |
| ShoulderSurfing | `curse.maven:ShoulderSurfing-243190:6543497` (NeoForge 1.21.4-4.12.0) | ShoulderSurfing publishes **no** 1.21.5 build at all, so the newest NeoForge build for any 1.21.x is used. `neoforge.mods.toml` says `[1.21.4-4.12.0,)`, which is the honest range. The Fabric build of the same version cannot be used: a plain `compileOnly` Maven dependency is not remapped by Loom, and the intermediary-named Fabric jar fails to compile against mojmap |

## 8. What the next port must not trust

1. **Do not trust the brief's "1.21.1 (old side) vs 1.21.11 (new side)" table.** For 1.21.5 the
   "new side" is a mix: `Identifier`, `ValueInput`/`ValueOutput`, `RenderSetup`/`RenderPipeline` +
   `RenderPipelines#PIPELINES_BY_LOCATION` public, `submit(…)`/`SubmitNodeCollector`,
   `net.minecraft.client.model.player.PlayerModel` and `AvatarRenderer` are all still 1.21.11-only,
   while the NBT `Optional` getters, the removal of `onRemove`, the `WallBlock.NORTH` rename,
   `ClientInput#getMoveVector`, the `TooltipDisplay` tooltip and the **string** recipe ingredients are
   already 1.21.5. PROMPT.md was corrected in this folder; check the jar, not the table.
2. **Do not assume the 1.21.4 tree boots.** Two of its mixins are broken against 1.21.4 (§5.1, §5.2).
   A port that inherits them inherits a hard boot failure. Check every `@Inject` handler's arity and
   every `@At` `INVOKE` target against the target jar, not against the previous port.
3. **Do not trust the Loom mixin annotation processor's silence.** It only warns for a *name* it
   cannot remap; a wrong descriptor or a wrong handler arity is not a compile error and not a warning.
4. **Architectury Loom's usable version is set by `architectury-plugin`, not by Minecraft** (§2).
5. **ModDevGradle's repository injection silently does nothing on Gradle 9** (§2).
6. **`curse.maven` coordinates must be mojmap builds and must already be in the Gradle cache** (§7).
7. **A fresh checkout needs two Gradle invocations**, not one: `./gradlew :common:build` and then
   `./gradlew build`. Architectury Loom resolves `:common`'s jar while it *configures* `:fabric`, and
   the root `build` → `bootstrap` dependency is only evaluated after configuration. Inherited from
   the 1.21.1 / 1.21.4 / 1.21.11 trees; documented in `BUILDING.md` rather than fixed. The failure
   looks like `Failed to read metadata from …/common/build/libs/parcool-1.21.5-3.4.3.3.jar,
   java.nio.file.NoSuchFileException`.
8. **Never run `./gradlew --stop` on this machine** — three ports build side by side out of the same
   Gradle home. Delete `.gradle/loom-cache` and the `build/` directories instead.
9. **An access-transformer method entry needs the full JVM descriptor, return type included.**
   NeoForge's `AtParser` rejects `(Ljava/lang/String;I…)` with `Invalid method descriptor` at the
   point it applies the transformer, which surfaces as a `createMinecraftArtifacts` failure, not as a
   compile error. Field entries carry no descriptor at all.

## 9. How this port was verified

`./gradlew :common:build && ./gradlew build` from a cleaned tree (`build/`, `.gradle/`,
`.architectury-transformer/` deleted) succeeds. Checked in the produced jars:

* Fabric jar: 29 mixin classes, `parcool.accesswidener` in `v2 intermediary` with all ten render-type
  entries remapped to `class_*` / `method_*` / `field_*`, **no refmap**, mixin targets statically
  remapped into the bytecode (`@Mixin(class_591)`, `setupAnim(class_10055)`), `fabric.mod.json` with
  `"version": "1.21.5-3.4.3.3"` and `"minecraft": "~1.21.5"`, the `META-INF/services` entry pointing
  at `FabricParCoolPlatform`, `assets/parcool/items/*.json` present, all five recipe files present in
  the **string** ingredient form.
* NeoForge jar: 29 mixin classes, **mojmap** bytecode (`net/minecraft/client/model/PlayerModel`,
  `net/minecraft/client/renderer/entity/state/PlayerRenderState`), `META-INF/neoforge.mods.toml` with
  `versionRange = "[21.5.0,)"` / `"[1.21.5,1.22)"` / architectury `"[16.0.0,)"`, the access
  transformer and the `LICENSE` it points at, the `META-INF/services` entry pointing at
  `NeoForgeParCoolPlatform`.
* The NeoForge access transformer was confirmed to have been **applied**: `javap` on the
  `compiledWithNeoForge` artifact ModDevGradle produced shows `RenderType.create` `public`,
  `RenderPipelines#PIPELINES_BY_LOCATION` and `#MATRICES_COLOR_FOG_SNIPPET` `public`, and
  `RenderStateShard#NO_TEXTURE` / `#LIGHTMAP` `public` — all of them `private`/`protected` in the
  plain 1.21.5 jar.
* All 38 mixin targets, `@Shadow` members, `@Accessor`/`@Invoker` names, `@At` `INVOKE` targets and
  handler signatures were checked against the 1.21.5 mojmap jar with `javap` (see §1 for the jar).
* `:common:checkCommonLoaderIndependence` passes (it is wired into `:common:check`).
* No `System.out`, no `printStackTrace`, no `TODO`, no absolute path anywhere in the sources or the
  build scripts, no unused imports (checked with a script over every `import` in all three modules).

**Not verified: the game.** No client, no server and no Prism instance was started — running
Minecraft was explicitly out of scope for this task. That means the mixin set has been verified
*statically* but **not** at runtime, and the play-through checklist from the brief's phase 6 (name
tags, stamina HUD, wall run, zipline ride, cross-loader join, vanilla keys still working next to
ParCool's) has not been performed.

The one place where that gap is a real risk rather than a formality is `client/renderer/RenderTypes`:
the two custom `RenderPipeline`s are built from `RenderPipelines#MATRICES_COLOR_FOG_SNIPPET`, the same
snippet `RenderPipelines.LEASH` is built from, and carry the same `core/rendertype_leash` shaders,
sampler and uniform block. A pipeline whose declared uniform set does not match what the shader
actually uses fails to compile at first draw, and nothing in this build would catch it. Treat a first
`:fabric:runClient` with a zipline in the world as the remaining step before shipping these jars to a
player.

## 10. Published artifacts

```
0.1-mc1.21.5fabric-3.4.3.3.jar     sha256 64d093979ab0be49e58f344548b1dd49a26f4c7e40f33fb0fd852b0b63b435af
0.1-mc1.21.5neoforge-3.4.3.3.jar   sha256 bca345bc16e8028d33cff1b99d7c0fb761b6a8bea89ad930f5f36708b99b99ee
```

copied to `/home/sanufsoii/ports/готовые порты/parcool/`.

### Re-published after the network decode fix

The first pair above is the artifact that carried the released-buffer decode. Both were replaced;
the network code is in the jar, so the bytes had to change and they did:

```
0.1-mc1.21.5fabric-3.4.3.3.jar     sha256 a70f5c6f8ec6e89d8cdbb9e0f74d59816e6d763ca61fce5ef1561026674d48b5
0.1-mc1.21.5neoforge-3.4.3.3.jar   sha256 4cf4d8e6804355a7ad71239c9a33afe4df8fa7cd50c09404b603c73a91f018a8
```

Still not verified by a run — see the end of this file.

## 11. The mixin check, mechanically

The 49 mixin references in this tree (every `@Mixin` target, every `@Inject`/`@Redirect`/
`@WrapWithCondition` `method =` with and without an explicit descriptor, every `@At` `INVOKE` target
including the two that name an inherited member - `LivingEntity;hurt` and `Player;setSprinting` -,
every `@Shadow`, and every `@Accessor`/`@Invoker` name) were resolved against the 1.21.5 mojmap jar
by script, name **and** descriptor, walking supertypes for inherited members. A second pass checked
the 18 inject/redirect/wrap handler arities against the arity of the method they target, which is the
check that catches the `KeyboardInputMixin` bug of §5.1 - the Loom annotation processor does neither.
`parcool-common.mixins.json` lists exactly the 29 mixin classes that exist on disk, no more and no
fewer, with `defaultRequire: 1`.

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

## The block registry key (`setId`) (added by the orchestrator, after the port was built)

`Blocks.java` built both hooks from `BlockBehaviour.Properties.of().mapColor(…).strength(…).sound(…)`
with no registry key. That is a hard crash on 1.21.5, and the build cannot see it. The evidence is
`javap` against the 1.21.5 mojmap jar this file already names in §1 — the plain remapped jar under
`.gradle/loom-cache/minecraftMaven/net/minecraft/`, **not** the `-sources` jar, which is generated
with the access widener already applied and therefore lies about visibility.

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

`effectiveDescriptionId()` is the same three lines with the same constant (`javap -c` output lines
653-664), and `BlockBehaviour`'s constructor calls **both** while the block is being constructed:

```
net.minecraft.world.level.block.state.BlockBehaviour(Properties)
  14: invokevirtual #102  // Properties.effectiveDrops:()Ljava/util/Optional;
  22: invokevirtual #108  // Properties.effectiveDescriptionId:()Ljava/lang/String;
```

So the `NullPointerException("Block id not set")` is thrown while
`new WoodenZiplineHookBlock(...)` / `new IronZiplineHookBlock(...)` run, i.e. while Architectury's
`DeferredRegister` is filling `Registries.BLOCK` — not later, when the game happens to ask for a
description, a model or a loot table. The item half of the same 1.21.2 change is in
`Item$Properties#effectiveDescriptionId()` (`javap -c` line 451) and `#effectiveModel()` (line 464),
both ending in `requireNonNull(id, "Item id not set")`, and `Items.java` already handles it.

Architectury's `DeferredRegister` does not set the key. Checked by running `javap -p -c` over every
class under `dev/architectury/registry` and `dev/architectury/impl` in `architectury-fabric-16.1.4.jar`
and grepping for `setId`: **zero** hits. Vanilla's `Blocks.register` and NeoForge's
`DeferredRegister.Blocks` both set it, which is exactly why the omission looks harmless while
developing against a NeoForge classpath and is fatal under Architectury.

**Fix** (`common/src/main/java/com/alrex/parcool/common/block/Blocks.java`): a small
`key(String)` helper and `.setId(key("wooden_zipline_hook"))` / `.setId(key("iron_zipline_hook"))`
on the two property chains, with the reason in the javadoc. The comment is not decoration: without
it the call looks like a duplicate of the name already passed to `REGISTER.register`, the next
reader removes it, and the mod dies at init with no compile-time signal. The 1.21.6 and 1.21.7 trees
carry the same call for the same reason.

**Warning for the next port.** `setId` is mandatory on the whole 1.21.2+ branch, not on 1.21.4 and
1.21.5 only. The read-only 1.21.1 tree has no `setId` anywhere, and that is *correct for 1.21.1* —
but a port that imports the 1.21.1 base and targets 1.21.2 or later inherits the crash from the
base, even though the base itself boots. Verify `Blocks.java` and `Items.java` against the **target**
jar, never against the tree the code was copied from. (The read-only 1.21.11 tree does have both —
`Blocks.java:33,43` and `Items.java:25` — so the 1.21.1 tree is the real trap here, not 1.21.11.)

### Checked in this pass, already correct, left alone

* `Item.Properties#useBlockDescriptionPrefix()` exists once in the 1.21.5 jar and is applied on
  **both** `BlockItem` properties via `Items.blockItemProperties(String)`, which only the two hooks
  call. Its body swaps in the `BLOCK_DESCRIPTION_ID` `DependantName`, which yields
  `block.parcool.*` — and `assets/parcool/lang/en_us.json` defines
  `block.parcool.wooden_zipline_hook` / `block.parcool.iron_zipline_hook` and no
  `item.parcool.*zipline_hook*`. Keys agree.
* `assets/parcool/items/*.json` is the right folder from 1.21.4: the target resources jar
  (`neoforge-21.5.98-minecraft-resources-aka-client-extra.jar`) has 1396 entries under
  `assets/minecraft/items/`. All three files are in the built jars; the neighbouring
  `models/item/*.json` files are inert and untouched.
* Recipes: already the **string** ingredient form, which is the only form 1.21.5 accepts (checked
  against the real `Ingredient.CODEC`, as in the section above), `"category": "misc"` present and
  optional, `minecraft:chain` correct for this version (`assets/minecraft/items/chain.json` exists,
  `iron_chain.json` does not). No change.
* `pack.mcmeta`: `version.json` in the target resources jar says
  `"pack_version": {"resource": 55, "data": 71}`. The file says `pack_format: 55` plus
  `supported_formats: [55, 71]`. 1.21.5's `PackMetadataSection` reads `pack_format` through
  `Codec.INT.fieldOf` and `supported_formats` through
  `InclusiveRange.codec(Codec.INT)` + `Codec.lenientOptionalFieldOf` (`javap -c`, `method_52434`),
  and `ExtraCodecs#intervalCodec` accepts both the list form `[55, 71]` and the object form
  `{"min_inclusive":…,"max_inclusive":…}` — confirmed by decoding all three shapes against the real
  `InclusiveRange.INT` of this jar. The list form is what ships, and it parses. There is no
  `min_format` / `max_format` on 1.21.5; those arrive with the 1.21.11 shapes. Nothing to change.
* `restoreVanillaBindings` is present and still needed: 1.21.5's `KeyMapping.MAP` is
  `Map<InputConstants$Key, KeyMapping>` — one mapping per physical key (`javap`, `net.minecraft.client.KeyMapping`
  line 4) — so ParCool's 16 shared keys still take right-click / Space / Left-Ctrl away from vanilla
  without the repair. The `KeyRecorder#onClientTick` call is still there.

### Not verified

Minecraft was not launched. The `setId` crash and its fix are established from the constructor
bytecode of the two target jars, not from a run, and the mixin set remains statically verified only.

## The network decode ran on a released buffer (found by launching the game, on 1.21.7)

The same defect was present in this port and was fixed here from the 1.21.7 report. Minecraft was
still not launched on 1.21.5, so this is a ported fix with no local run behind it.

### The stack that found it (live 1.21.7 client)

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

It repeats every few seconds. The server task dies on every incoming ParCool packet, the limitation
snapshot never arrives, and no action can start — which is what "almost nothing worked" meant.

### Why it happened

One line, identical in `FabricParCoolNetwork` and `NeoForgeParCoolNetwork`:

```java
(buf, context) -> context.queue(() -> handler.accept(erased.decode((RegistryFriendlyByteBuf) buf), context))
```

`NetworkManager.registerReceiver` hands the receiver a **raw** `RegistryFriendlyByteBuf` and releases
it as soon as that lambda returns. `context.queue(...)` moves the work to the main thread, i.e. to
*after* the release. The decode therefore reads memory netty has already freed, and the first
`VarLong.read()` in any ParCool payload codec throws `IllegalReferenceCountException: refCnt: 0`.

The queue is needed for **thread safety of the handler** — the handler touches the player, the level
and the mod's own state, none of which may be touched from a netty thread. The queue is **not**
needed for decoding. Decoding is pure byte-reading: safe on the netty thread, and only safe there.
The correct order is decode first, queue only the handler:

```java
(buf, context) -> {
    T payload = erased.decode((RegistryFriendlyByteBuf) buf);
    context.queue(() -> handler.accept(payload, context));
}
```

`NetworkChannel`, which the 1.21.3 Fabric path uses, has always had this shape — the channel decodes
in its own decoder lambda and hands an already-built payload to the receiver, so
`context.get().queue(() -> handler.accept(payload, …))` only ever queues the handler. The move to the
raw id-based `NetworkManager` API collapsed those two lambdas into one and dragged the decode into
the queue with them. That collapse is the entire regression, and it is why the 1.21.3 file is the
reference for the correct order.

The comment in the source is mandatory and must not be deleted or shortened: without it the next
reader sees a decode that "could just as well" happen inside the `queue`, concludes the rewrite is
equivalent, and puts the bug straight back.

### This class of defect is invisible to everything except a live client

Recorded separately on purpose, because it is the general lesson and not just this port's story:

* `javac` sees `StreamCodec.decode(ByteBuf)` return `T` and `PacketContext.queue(Runnable)` return
  `void`. Both signatures are correct. The bug lives entirely in *when* the call happens relative to
  a release that happens outside the type system, in a lambda Architectury invokes.
* `IllegalReferenceCountException` is a runtime netty invariant, not a type error. No annotation, no
  null check and no assertion would flag it.
* Nothing in the build exercises it. `:common:checkCommonLoaderIndependence` is a source-level
  loader-leak check, the mixin checker is a static target check, and there is no client, no packet
  capture and no integration test. Green build, green acceptance, dead mod — which is exactly what
  1.21.5, 1.21.6 and 1.21.7 all reported before 1.21.7 was finally launched.
* **The packets have to actually flow.** Without a client entering a world there is no inbound
  ParCool packet, so the lambda never runs and the bug cannot manifest. A dedicated server with no
  client connected proves nothing here either.
* The only detector is a real client on a real server, and then reading the log. `refCnt: 0` in a
  stack trace is the signature.

## 1x. The dedicated Fabric server did not start: `Adaptor#registerS2C` is stripped on a server

*(The last defect in this port's history, and the first one that only a dedicated server could see.
It was found from the live 1.21.4 log, not by anything in the build.)*

### The stack, from a live `runServer` before the fix

```
[main/ERROR] (Minecraft) Failed to load eula.txt
...
Caused by: java.lang.AbstractMethodError: Receiver class
  dev.architectury.networking.fabric.NetworkManagerImpl$1 does not define or inherit an
  implementation of the resolved method 'abstract void registerS2C(
  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type,
  net.minecraft.network.codec.StreamCodec,
  dev.architectury.networking.NetworkManager$NetworkReceiver)'
  of interface dev.architectury.impl.NetworkAggregator$Adaptor
  at dev.architectury.impl.NetworkAggregator.registerS2CReceiver(NetworkAggregator.java:119)
  at dev.architectury.impl.NetworkAggregator.registerReceiver(NetworkAggregator.java:76)
  at dev.architectury.networking.NetworkManager.registerReceiver(NetworkManager.java:93)
  at com.alrex.parcool.platform.FabricParCoolNetwork.register(FabricParCoolNetwork.java:45)
  at com.alrex.parcool.common.network.NetworkRegistries.registerS2C(NetworkRegistries.java:93)
  at com.alrex.parcool.ParCool.init(ParCool.java:78)
  at com.alrex.parcool.ParCoolFabric.onInitialize(ParCoolFabric.java:10)
```

The counters on that same run read `Registering S2C receiver: 0` and `Registering C2S receiver: 1`
before the throw, and the `Done` count 0 - the **first** S2C receiver kills the JVM, so nothing after
it ever registers.

### Root cause

`NetworkAggregator.registerReceiver(Side.S2C, ...)` delegates to `Adaptor#registerS2C`. `javap -v` on
`dev/architectury/networking/fabric/NetworkManagerImpl$1.class` inside `architectury-fabric-16.1.4.jar` shows that
`registerS2C` **is** declared, with exactly the descriptor the interface declares - so this is neither
a version skew nor a stale jar - and that it is the only member of `NetworkAggregator.Adaptor`
carrying

```
RuntimeInvisibleAnnotations:
  net.fabricmc.api.Environment(value = EnvType.CLIENT)
```

which is exactly what the Architectury source says it is: the body of `registerS2C` calls
`ClientPlayNetworking.registerGlobalReceiver`, a client-only API. Fabric Loader's `EnvironmentStripper`
deletes `@Environment(CLIENT)` members when the game runs on a dedicated server, so
`NetworkManagerImpl$1` reaches the aggregator without `registerS2C` and the first `registerS2CReceiver`
call throws `AbstractMethodError`. `registerC2S`, `registerS2CType` and both `toXxxPacket` methods
carry no such annotation and are present on both sides - which is why the C2S counter reaches 1 while
S2C registers 0 times.

### The fix

`fabric/src/main/java/com/alrex/parcool/platform/FabricParCoolNetwork.java`, at the top of `register`:

```java
if (clientbound && Platform.getEnvironment() == Env.SERVER) {
    NetworkManager.registerS2CPayloadType(wireId);
    return;
}
```

`NetworkManager.registerS2CPayloadType(ResourceLocation)` is precisely what Architectury's own javadoc
on that method prescribes ("For S2C types, `registerReceiver` should be called on the client side,
while `registerS2CPayloadType` should be called on the server side"). It fills
`NetworkAggregator.S2C_TYPE` / `S2C_CODECS` / `S2C_TRANSFORMERS` and reaches the loader through
`Adaptor.registerS2CType`, the unstripped sibling of `registerS2C`, minus the client-only
`ClientPlayNetworking.registerGlobalReceiver` call. A server never *receives* a server-to-client
packet, so the receiver is dead weight there; its only required job is making the payload type
sendable.

The wire format does not change. Both shapes put `BufCustomPacketPayload.streamCodec(type)` under the
same id in the aggregator, and the real ParCool codec only ever runs locally - on send inside
`NetworkAggregator.collectPackets`, on receive inside the receiver lambda - so a packet travels as
`BufCustomPacketPayload(id, <the bytes ParCool encoded>)` either way. The NeoForge module and the
common `ParCoolNetwork` interface are untouched: the branch lives entirely inside the Fabric
implementation.

`NetworkChannel` would have been **worse**, not better: its `register` wraps the S2C half in
`if (Platform.getEnvironment() == Env.CLIENT)`, so on a server only the C2S half of the aggregator is
populated and `NetworkChannel#sendToPlayer` -> `NetworkManager.toPacket(s2c(), ...)` then does
`new BufCustomPacketPayload(S2C_TYPE.get(id), bytes)` with a `null` type - a `NullPointerException` on
every single server-to-client packet. A green `Done` would have hidden that. This port stays on the
id-based path with a side split, so one set of wire ids serves both loaders.

The released-buffer decode order from the earlier fix was **not** touched: the decode is still before
`context.queue(...)`, so `IllegalReferenceCountException: refCnt: 0` cannot come back.

### Evidence that the fix works - a real dedicated server

`./gradlew :fabric:runServer` on this port, from a wiped loom cache:

```
[12:57:42] [Server thread/INFO] (Minecraft) Starting Minecraft server on *:25565
[12:57:46] [Server thread/INFO] (Minecraft) Done (3.063s)! For help, type "help"
```

`grep AbstractMethodError` on that log is empty, and the registration lines now show only the four
C2S receivers (`parcool:payload.custom_stamina.c2s`, `.action_state.c2s`, `.client_info.c2s`,
`.stamina.c2s`) - the S2C half is registered by type instead, which is what a server needs.

### This class of defect is invisible to the compiler, to the client and to singleplayer

Recorded separately on purpose, because it is the general lesson:

* `javac` is happy. `registerS2C` is present in the jar being compiled against, `@Override` resolves
  and the call type checks. No annotation, no null check and no assertion would flag it.
* **Singleplayer cannot see it.** The integrated server lives inside the *client* JVM, where
  `@Environment(CLIENT)` members survive stripping, so the identical call succeeds. So does a
  dedicated *client*. Only a dedicated server JVM strips the member.
* Nothing in the build exercises it: `checkCommonLoaderIndependence` is a source-level loader-leak
  check, the mixin checker is a static target check, and there is no networking integration test.
* **A green client and a green singleplayer session prove nothing here.** A dedicated server run is
  mandatory before a port may be called done. Same lesson as the released-buffer decode defect, one
  step further out: that one needed packets to actually flow, this one only needs the server to boot.

### 1x-b. A *second* dedicated-server-only defect that this one was hiding

With the S2C registration fixed, 1.21.5 still would not start, and the second crash is worth
recording because it is the same species of bug and because it was invisible until the first one was
out of the way:

```
Caused by: java.lang.BootstrapMethodError: java.lang.RuntimeException: Cannot load class
  net.minecraft.client.player.LocalPlayer in environment type SERVER
    at knot/com.alrex.parcool.common.action.Actions.<clinit>(Actions.java:15)
    at knot/com.alrex.parcool.config.ParCoolConfig$Client.<init>(ParCoolConfig.java:541)
    at knot/com.alrex.parcool.config.ConfigSpec$Builder.configure(ConfigSpec.java:258)
    at knot/com.alrex.parcool.config.ParCoolConfig$Client.<clinit>(ParCoolConfig.java:61)
    at knot/com.alrex.parcool.config.ParCoolConfig.load(ParCoolConfig.java:1025)
    at knot/com.alrex.parcool.ParCool.init(ParCool.java:81)
```

`ParCool.init` builds **both** config specs on both sides, so the client spec's constructor runs on a
dedicated server, and its field initialisers size three arrays with `Actions.LIST.size()`. That runs
`Actions`' static initialiser, which links every `Action` subclass - and linking verifies it, and the
verifier resolves `LocalPlayer` as soon as a class mentions it. `LocalPlayer` carries a class-level
`@Environment(EnvType.CLIENT)`, so `EnvironmentStripper` refuses to load it in a SERVER JVM at all:
`FabricTransformer.transform` throws before the class is ever defined. Note that `ParCool.init:81`
is *after* `ParCool.init:78`, the network registration the first defect died on - the
`AbstractMethodError` was masking this one completely.

Which class triggers it is version-specific and had to be found by measurement, not by reading. A
temporary probe was compiled into `Actions` (one `System.out.println` per registry entry) so the
stack trace's line number named the culprit: the failure was at the **`ChargeJump`** entry, and
1.21.5's `ChargeJump` is the only one of these four ports that still read `cp.input.keyPresses.*`
through a `LocalPlayer`. It was rewritten the way 1.21.2 already had it - guard with
`player.isLocalPlayer()`, then use `player.onGround()` / `isVisuallyCrawling()` / `isSprinting()` /
`isShiftKeyDown()` and `KeyBindings.isAnyMovingKeyDown()` (which reads the same
`Minecraft.getInstance().player.input`) - so `ChargeJump` no longer mentions `LocalPlayer` at all.
For consistency with 1.21.2 / 1.21.6 / 1.21.7 the remaining `player instanceof LocalPlayer` pattern
matches on this port were changed to the `isLocalPlayer()` + cast form as well (`BreakfallReady`,
`HorizontalWallRun`, `RideZipline`, `SkyDive`, `Stamina.consume`/`recover`,
`LimitationPayload.handleClient`). Those are behaviour-identical; the probe showed `BreakfallReady` in
particular is happy with the cast form, and 1.21.2 boots with it.

**The trap that cost the most time:** `./gradlew :fabric:runServer` will happily run a *stale*
`.gradle/loom-cache/remapped_mods/.../common-<hash>-<version>.jar`. Loom keys that remap on the
project coordinates, not on the jar's contents, so an edited `common` source can be silently ignored -
a "fix" that appeared not to work had in fact never been executed. Wiping

```
.gradle/loom-cache fabric/build/loom-cache common/build/loom-cache \
common/build/devlibs neoforge/build/explodedCommon
```

before the acceptance run is not a formality; it is the difference between measuring the new code and
measuring the old one. `:common:remapJar` has to run first, because Loom reads metadata out of
`common/build/libs/parcool-<version>-3.4.3.3.jar` while it *configures* `:fabric` - and deleting
`common/build/libs` outright breaks configuration outright
(`java.io.UncheckedIOException: Failed to read metadata from .../parcool-1.21.5-3.4.3.3.jar`).

### Published artifacts after the dedicated-server fix

```
0.1-mc1.21.5fabric-3.4.3.3.jar     sha256 4975c66772e36d78c4b4dde3ac85f7a16e65e31355728addeaaacc1b174ec59d
0.1-mc1.21.5neoforge-3.4.3.3.jar   sha256 1165e0123ab5bcd166d160b025a3907ccba9a0bf88fb61acec16d61feb4b9c37
```

copied to `/home/sanufsoii/ports/готовые порты/parcool/`.

* Fabric jar: **changed, as it must be** against the previously published `a70f5c6f8ec6e89d8cdbb9e0f74d59816e6d763ca61fce5ef1561026674d48b5`. the branch above is Fabric-only code and it is in the jar.
* NeoForge jar: **changed** against the previously published `4cf4d8e6804355a7ad71239c9a33afe4df8fa7cd50c09404b603c73a91f018a8`. the `common` changes are in it.
* `:common:checkCommonLoaderIndependence`, `:common:build` and `build` are all green.
* Verified inside the published Fabric jar: `FabricParCoolNetwork` calls `registerS2CPayloadType`.
* **Not verified:** no client was launched (the orchestrator holds the single GPU), and the server was
  booted with no players, so a real server -> client packet write has not been observed end to end.
  What is proven is that the server starts and registers the S2C payload *types*.

## Live-client result (human player, not an orchestrator)

Fabric client, human in a world: **all actions, zipline, HUD, settings and inventory textures work.
Approved.** Fabric dedicated server reaches `Done (` with `AbstractMethodError` 0.

Run under three clients at once (1.21.5, 1.21.6, 1.21.8). At boot on this build:
`Mixin apply failed` 0, `IllegalReferenceCount` 0, `GL ERROR` 0, `Invalid key` 0.

Still not exercised on this port: NeoForge (never launched on any port), and the cross-loader
join (a NeoForge client against this Fabric server).

## Live-client result (human player, not an orchestrator)

**NeoForge dev client, human in a world: all actions, zipline, HUD, settings and inventory textures
work. Approved.**

Log counters on that run, all zero: `IllegalAccessError`, `Exception caught during firing event`,
`Ticking player`, `Registry is already frozen`, `IllegalReferenceCount`, `NullPointerException`,
`GL ERROR`, `Invalid key`, `Mixin apply for mod parcool failed`, `BUILD FAILED`. `joined the game` = 1.

This is the first NeoForge run on this port, so the attribute-registration split - register
`Attributes.registerAll()` only from the Fabric entry point and resolve the holder on NeoForge - is
now confirmed rather than merely argued from the NeoForge sources. It survives a real
`Player#createAttributes`, which is the step that dies with "Registry is already frozen" if the
split is wrong.

Still not exercised on this port: the NeoForge dedicated server, and the cross-loader join.
