# NOTES.md — ParCool on the Architectury API, Minecraft 1.21.2

Working notes for this port: where every pinned number came from, every version delta that had to be
decided, every upstream bug found (and not fixed), and what the next port must not trust.

**Read this before trusting any line of the 1.21.11 or 1.21.1 reference trees for 1.21.2.** Minecraft
1.21.2 is *neither* of them: it is a genuine hybrid. Roughly two thirds of the client/render/entity
API is already on the "1.21.11" shape, and a third is still on the "1.21.1" shape. Guessing from a
version number instead of from the jar costs hours.

---

## 1. The resolved toolchain, and where each number came from

| Key | Value | Source |
|---|---|---|
| `minecraft_version` | `1.21.2` | the task |
| `neo_version` | `21.2.1-beta` | `https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge` → the whole 21.2 line is **`21.2.0-beta` and `21.2.1-beta`, two builds and nothing else**. Its pom pins `neoform 1.21.2-20241022.151510`, so it is the newest (and effectively the only usable) NeoForge for this Minecraft. |
| `loader_version` | `0.16.10` | `https://meta.fabricmc.net/v2/versions/loader/1.21.2`. 0.16.x is the line contemporary with 1.21.2; 0.19.x also works but belongs to the 1.21.9+ era and brings the 0.19 access-widener strictness the 1.21.11 tree documents. |
| `fabric_api_version` | `0.106.1+1.21.2` | `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`. The newest build published for 1.21.2 is 0.106.1; 0.107.0 already moved to 1.21.3. (Modrinth only lists 0.106.1 for 1.21.2 too, but the maven metadata is the authority and it shows the 0.102–0.106 ladder.) |
| `architectury_api_version` | `14.0.4` | `https://maven.architectury.dev/dev/architectury/architectury-fabric/maven-metadata.xml` plus the `fabric.mod.json` inside each jar. 13.0.11 declares `minecraft: ~1.21-`; **14.0.4 declares `minecraft: ~1.21.2-`**; 15.0.3 already declares `~1.21.4-`. `architectury-neoforge` 14.0.4 requires MC `[1.21,)` / NF `[21.0.110-beta,)`. |
| `dev.architectury.loom` | `1.7.435` | the 1.21.1 reference port. Loom 1.7 configures Minecraft 1.21.2 (it prints an "outdated version" warning, which is expected and harmless). Loom ≥ 1.9 removed `useLegacyMixinAp`, and Loom 1.17 does not configure a NeoForge 21.2 setup at all. |
| `architectury-plugin` | `3.5.170` | both reference trees. |
| `net.neoforged.moddev` | `1.0.24` | `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml`. 1.0.22 and 1.0.25 do not exist; 1.0.24 is the newest 1.0.x. |
| Gradle wrapper | `8.10.2` | ModDevGradle 1.0.x line, same as the 1.21.1 tree. |
| Java toolchain | 21 | `java { toolchain { languageVersion = 21 } }` in the root `build.gradle`. |
| `org.gradle.jvmargs` | `-Xmx2G` | set deliberately: three agents build in parallel and 21 GiB of RAM is shared. |

### The two metadata URLs in PROMPT.md that are wrong

* `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/net.neoforged.moddev.gradle.plugin/maven-metadata.xml`
  → **404**. The plugin-marker path does not exist. Use
  `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml`.
* `https://maven.architectury.dev/dev/architectury/loom/maven-metadata.xml` → **404**. Loom is
  published as a Gradle plugin marker:
  `https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml`.

---

## 2. Which side of the 1.21.1 / 1.21.11 seam 1.21.2 is on

Everything below was read out of the **mojmap 1.21.2 jar** (Loom's
`minecraftMaven/.../minecraft-merged-1.21.2-...jar`, checked with `javap`) and out of the decompiled
`genSources` tree. `javap` is the authority; see §6 for why the decompiled sources are not.

### Already on the 1.21.11 side (so the 1.21.11 code is correct here)

| Area | 1.21.2 reality |
|---|---|
| Entity render states | `EntityRenderer<T, S extends EntityRenderState>`, `createRenderState()`, `extractRenderState(T, S, float)`, `LivingEntityRenderer<T, S, M>`, `PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerRenderState, PlayerModel>`. **But** the draw call is still `render(S, PoseStack, MultiBufferSource, int)` — `SubmitNodeCollector` does not exist. |
| `PlayerModel` | no type parameter, `extends HumanoidModel<PlayerRenderState>`, `setupAnim(PlayerRenderState)`, and the sleeve/pants/jacket parts are **children** of the limbs (`leftArm.getChild("left_sleeve")`). |
| `Player#jumpFromGround` | moved up to `LivingEntity#jumpFromGround`. `Player` has no such method, so a `Player`-targeted `@Inject` never resolves. |
| `Entity#getBoundingBoxForCulling` | moved to `EntityRenderer#getBoundingBoxForCulling(T)`. |
| `Entity#hurt` | `public final void hurt(DamageSource, float)`; entities implement `abstract boolean hurtServer(ServerLevel, DamageSource, float)`. `LivingEntityFallMixin`'s redirect target and its handler's return type both follow. |
| `Registry#get` | returns `Optional`; `Registry#getHolder(ResourceKey)` is gone. |
| `BlockEntityType` | `Builder` deleted, `register(String, factory, Block...)` private → the `BlockEntityTypeInvoker` approach. |
| `ItemInteractionResult` | deleted → `InteractionResult` with `PASS` / `SUCCESS` / `SUCCESS_SERVER` / `CONSUME`. |
| `FastColor.ARGB32` | deleted → `ARGB`. |
| `SimpleCraftingRecipeSerializer` | deleted → `CustomRecipe.Serializer` (whose factory takes a `CraftingBookCategory`, i.e. the recipe's own constructor signature). |
| `CustomRecipe#canCraftInDimensions` | deleted. |
| `Potion` | constructor is `Potion(String name, MobEffectInstance...)`. |
| `PotionBrewing.Builder#addMix` | takes `Holder<Potion>` on both sides. |
| `EntityType.Builder#build` | takes a `ResourceKey<EntityType<?>>`, not a `String`. |
| `ClientInput` | `keyPresses` (`Input` record with `forward()/backward()/left()/right()/jump()/shift()/sprint()`) is there; `leftImpulse` / `forwardImpulse` **also** still are. |
| `GuiGraphics#blit` | the `RenderType` factory is the first argument and `u`/`v` are floats; the plain `blit(ResourceLocation, …)` overload is gone. The factory to use is `RenderType::guiTextured` (not 1.21.11's `RenderPipelines.GUI_TEXTURED`). |
| `Minecraft#getTimer` | gone → `Minecraft#getDeltaTracker()`. |
| `ClientLevel#addDestroyBlockEffect` | present (delegates to `ParticleEngine#destroy`). |
| `Block#updateShape` | `updateShape(LevelReader, ScheduledTickAccess, BlockPos, Direction, BlockPos, BlockState, RandomSource)`. |
| `Level`'s constructor | lost the `Supplier<ProfilerFiller>` argument. |
| `LivingEntity#getVisibilityPercent` | public, so `PlayerInteractionVisibilityMixin` can use the upstream 0.1 factor. |
| `ArgumentTypeInfos#bootstrap` | public; `register(…)` is still **private**. |

### Still on the 1.21.1 side (so the 1.21.1 code is correct here)

| Area | 1.21.2 reality |
|---|---|
| `ResourceLocation` | still called `ResourceLocation`; `net.minecraft.resources.Identifier` does not exist. Every one of the 30 files that used `Identifier` was rewritten. |
| `RenderType` | still `RenderStateShard` composites; `RenderSetup` / `RenderPipeline` / `RenderPipelines` do not exist. `ZiplineRopeRenderer` therefore draws through a `MultiBufferSource` with a `RenderType`, exactly as in 1.21.1. |
| `KeyMapping` | `category` is a `String` (no `KeyMapping.Category`), and **`KeyMapping.MAP` is `Map<InputConstants.Key, KeyMapping>` — one mapping per physical key.** The `restoreVanillaBindings` repair from the 1.21.1 tree is therefore *required* here, together with the `KeyRecorder#onClientTick` call that drives it. Removing it makes ParCool steal right-click / Space / Left-Ctrl from vanilla on Fabric. |
| `KeyMapping#key` | still `private`, so `KeyMappingMixin`'s shadow has to stay `private`. |
| Entity / BlockEntity save | `CompoundTag` (`readAdditionalSaveData` / `addAdditionalSaveData` / `saveAdditional` / `loadAdditional`); no `ValueInput` / `ValueOutput`. `ZiplineInfo.CODEC` and `ZiplineType.CODEC` are 1.21.11-only and were deleted. |
| `Entity#isInWaterOrBubble` | present. `utilities/EntityUtil.isInWaterOrBubble` (a 1.21.11 reimplementation) was deleted and all 15 call sites went back to the vanilla accessor — the vanilla flag is authoritative, the block test is a re-derivation of it. |
| `Item#appendHoverText` | `appendHoverText(ItemStack, TooltipContext, List<Component>, TooltipFlag)` — the `Consumer` + `TooltipDisplay` form is 1.21.11. |
| `Block#onRemove` | still there (protected). `BlockEntity#preRemoveSideEffects` does not exist. |
| `WallBlock` | the properties are `NORTH_WALL` / `SOUTH_WALL` / `EAST_WALL` / `WEST_WALL`, not `NORTH` / … |
| `BlockBehaviour#onRemove`, `Item#use` | unchanged. |
| `Level#damageSources()` | **exists**, but `DamageSources#source(ResourceKey)` is **private**, which is why `FabricParCoolPlatform` builds the `DamageSource` by hand while the NeoForge side uses the loader's widened call. The NeoForge access transformer entry `public net.minecraft.world.damagesource.DamageSources *()` is what makes that work. |
| `LevelResource(String)` | public → no access-widener / access-transformer entry needed (the 1.21.1 widener had one). |
| `LivingEntity#causeFallDamage` | `(float, float, DamageSource)` — 1.21.11 widens the distance to `double`, 1.21.2 does **not**. A handler declared `(double, float, DamageSource, CallbackInfo)` compiles and then never applies. |
| `Player#attack` | still contains the inline `setSprinting(false)`, so `PlayerMixin`'s `@WrapWithCondition(method = "attack", …)` is right; there is no `causeExtraKnockback`. |
| Recipe ingredient JSON | the string form (`"minecraft:iron_nugget"`, `"#minecraft:logs"`), not the object form. |
| `Animation#setAnimator(Animator)` + `new XAnimator()` | 1.21.1's form works. **This port keeps the 1.21.11 reflective `setAnimator(Class, Object…)`** — see §4. |
| `sounds.json` vanilla ids | the leash-knot sounds are `entity/leashknot/place1..3` in 1.21.2 (they become `leash1..3` later). Checked against the 1.21.2 asset index. |
| `pack.mcmeta` | see §5. |

### Per-loader split that the reference ports keep

* **Fabric** — Architectury's raw `NetworkManager` id-based API works on 14.0.4, so both loaders use it
  and the wire ids match (`parcool:payload.*` plus a `.c2s` variant). Verified without running the game:
  `javap` on `architectury-fabric-14.0.4.jar` shows `registerC2S` **and** `registerS2C` on
  `NetworkManagerImpl$1`, and `javap -c` on `NetworkAggregator#registerReceiver` shows the
  `ResourceLocation` overload filling `C2S_TYPE` / `S2C_TYPE` before delegating. architectury-fabric
  13.0.11 did not implement `registerS2C`, which is why the 1.21.1 tree has to use `NetworkChannel` on
  Fabric and ends up with two different id schemes. If a future Architectury line ever drops
  `registerS2C` again, that is the flag to watch.
* **NeoForge** — mojmap-named bytecode, `architectury-plugin`'s `neoForge()` transform unused, `:common`'s
  `transformProductionNeoForge` artifact consumed as a file dependency, exactly as in 1.21.11.
* **Access** — `parcool.accesswidener` for Fabric, `META-INF/accesstransformer.cfg` for NeoForge, with
  the NeoForge side written in mojmap names. Both were re-derived for 1.21.2 (see §5).

---

## 3. Bugs found in the reference trees

### Found *and* carried over (they are version-independent)

1. **`ConfigSpec#persist()` rewrote the whole file on every `set()`.** The settings screens call
   `set()` from `apply`, once per rendered frame while a slider is being dragged, so a single drag
   produced hundreds of full-file writes. Fixed in 1.21.11 by marking the spec dirty and writing once
   from `save(Path)`.
2. **`ZiplineRopeEntity#addAdditionalSaveData` wrote `"Tile1_X"` three times** (and `"Tile2_X"` three
   times), so only the X component of each rope end ever reached disk. Every zipline collapsed onto a
   1×0×1 line after a chunk reload. Six distinct keys now.
3. **`KeyBindings#isDown` / `isMetaKeyDown` polled unbound keysyms.** `GLFW_KEY_UNKNOWN` is `-1` and
   GLFW answers `GLFW_INVALID_ENUM` once per unbound key per poll, tens of lines per second. The
   `isPollableKeysym` guard is present.
4. **`Entity#move` re-entrancy → `StackOverflowError`.** The movement enforcer applies its point with a
   virtual `move` call that lands back in the injection that asked for it. The guard lives on
   `BehaviorEnforcer` as a `ThreadLocal`, because a `@Unique static` field on a mixin class that does
   not extend its target is silently dropped by Mixin.
5. **Remote-player animators ran at the frame rate.** The local player was advanced twice and the whole
   mod played ~10× too fast at 200 fps. This port inherits the 1.21.11 fix: remote animators advance
   from `ClientTickEvent.CLIENT_PRE`.
6. **Fabric packaging**: the access widener shipped in `v2 named`, which Fabric Loader rejects on a
   production client, and `sourceSets.main.output` leaked mojmap loader classes into an intermediary
   jar. Both are fixed in the inherited `fabric/build.gradle`.
7. **The dead full-bounding-box ladder branch** in `LivingEntityMixin` was deleted rather than left
   behind as dead code: it carried an `int < double` comparison that let a block on `floor(maxY)` pass
   and a bounds check against `pos` where it meant the scanned position.

### Found in the reference trees, *not* carried over (deliberately)

8. **`BufferUtil` lost its overflow checks in 1.21.11.** The 1.21.11 tree made `ensureRoom` `static`
   and pointed it at a `current` field, then removed the two `ensureRoom(…)` calls from
   `putVector3i` and `putVec3` — the two biggest writers, 12 and 24 bytes. This port keeps the 1.21.1
   per-instance `ensureRoom` with all four checks, so a payload that outgrows `SYNC_BUFFER_SIZE` still
   fails loudly instead of writing past the limit.
9. **`Animation#setAnimator(Class, Object…)` uses reflection** to find a constructor by arity. It is
   kept (see §4), but the 1.21.1 `new XAnimator(args)` is strictly more type-safe; switching back is a
   mechanical, behaviour-preserving change if anyone wants it.

### Upstream ParCool bugs (recorded, not fixed)

10. **`assets/parcool/sounds.json` in the 1.21.1 tree** references
    `minecraft:entity/leashknot/place1..3`, which is correct for 1.21.1 *and* for 1.21.2. The 1.21.11
    tree has `leash1..3`, correct for 1.21.11. Neither is an upstream bug; this is a reminder that the
    ids moved and the file has to be checked per version. All 20 other vanilla sound references in the
    file were verified against the 1.21.2 asset index — none missing.
11. **`Dodge#canStart` and `FastRun` carry the same `isInWaterOrBubble` clause twice** (upstream
    duplication). Harmless; left alone.
12. **`Action#wantsToShowStatusBar` / `getStatusValue` take a `Player` instead of a `LocalPlayer`.**
    That is not an upstream bug but a port decision inherited from 1.21.11: `Actions`' static
    initialiser is reached on a dedicated server, and a client-only parameter in a signature the
    verifier has to resolve drags `LocalPlayer` into the server frame. Harmless and strictly safer.

---

## 4. Deliberate decisions that a reader might mistake for mistakes

* **The reflective `Animation#setAnimator(Class, Object…)` was kept.** Its stated motivation is the
  1.21.11 dist-cleaner/verifier behaviour, which 1.21.2 does not have (the 1.21.1 tree instantiates
  animators with `new` and boots on the same NeoForge generation). Keeping it was cheaper and lower
  risk than rewriting 27 actions, and it is a single documented seam rather than 27 scattered
  reflective calls. See bug 9 above.
* **`PlayerInteractionVisibilityMixin` uses `LivingEntity#getVisibilityPercent` × 0.1**, not 1.21.1's
  `Player#canInteractWithEntity → false`. Both work in 1.21.2; the 0.1 factor is what upstream does and
  it survives a later removal of the tracker's use of the predicate.
* **`ClientLevel#addDestroyBlockEffect` is used instead of `ParticleEngine#destroy`.** In 1.21.2 the
  former is a one-line delegation to the latter, so this is a style choice, not a requirement.
* **The four optional NeoForge integrations are compiled against their newest *1.21.1-line* builds**
  (Paraglider 21.1.3, ShoulderSurfing `[1.21,)`, BetterThirdPerson `[1.21,)`, EpicFight 21.17.3.1).
  None of them publishes a 1.21.2 build, and none is on Modrinth for 1.21.2 either. All four report
  themselves absent at runtime when the class is not loadable, so a client that has a real 1.21.2 build
  of one of them works unchanged. The 1.21.11 tree makes the same trade for EpicFight. If a port ever
  needs one of them at runtime, this is the thing to revisit.
* **`pack.mcmeta` uses `supported_formats`** (see §5) — the 1.21.1 tree ships a bare `pack_format: 34`,
  the 1.21.11 tree ships `81` plus `min_format` / `max_format`, and **neither of those two forms is
  correct for 1.21.2**. `min_format` / `max_format` do not exist in 1.21.2 at all (see §5).

---

## 5. `pack.mcmeta`, verified

`net/minecraft/DetectedVersion.java` in the 1.21.2 sources says
`resourcePackVersion = 42`, `dataPackVersion = 57`.
`net/minecraft/server/packs/metadata/pack/PackMetadataSection.java` in 1.21.2 accepts exactly
`description`, `pack_format` and an optional `supported_formats` — an `InclusiveRange<Integer>` read
from a two-element array — and `Pack#getDeclaredPackVersions` **discards the range entirely if
`pack_format` is not inside it**.

So the shipped file is:

```json
{
    "pack": {
        "description": "ParCool mod resources",
        "pack_format": 34,
        "supported_formats": [34, 57]
    }
}
```

`pack_format` stays 34 (the 1.21.1 value, so the two sibling ports agree on the on-disk form) and
`34..57` covers both the client (42) and the server (57) check, which a bare `34` does not.
The 1.21.11 file (`81` + `min_format` + `max_format` + `supported_formats`) would be read as a
declared range of 81..81 by 1.21.2, i.e. `TOO_NEW`, and the mod's recipes, tags, loot tables and damage
types would silently be skipped.

---

## 6. What the next port must not trust

* **Do not trust `genSources` output for access levels.** The decompiler prints the access of the
  *Loom-generated, access-widener-applied* jar, so every field the mod widens comes back as `public`.
  That is how `LivingEntity#swimAmount`, `Entity#onGround`,
  `Player#canPlayerFitWithinBlocksAndEntitiesWhen` and `LevelResource#<init>` all looked public here
  while the vanilla 1.21.2 classes have them `private` / `protected`. Use `javap -p` on the **un-widened**
  jar under `.gradle/loom-cache/minecraftMaven/…/` (the variant whose hash directory differs from the
  one the sources came from) or just read the compiler's own error.
* **Do not trust the 1.21.1 tree's `parcool.accesswidener` or the 1.21.11 tree's.** The right set for
  1.21.2 is the 1.21.1 file minus the `LevelResource` entry, and the right NeoForge AT is the 1.21.1
  file plus a comment. Both were re-derived from `javap`.
* **Do not trust that a mixin target which compiles also resolves.** javac does not type-check a
  `@Inject` handler against its target. `LivingEntityFallMixin` declared
  `onFall(double, float, DamageSource, CallbackInfo)` and compiled cleanly while 1.21.2's
  `causeFallDamage` takes a `float` — the injection would have been a silent no-op, i.e. a hard boot
  failure with `defaultRequire: 1`. All 39 mixin targets in this tree were checked mechanically against
  the mojmap 1.21.2 jar with `javap` (names *and* descriptors, plus every `@Shadow`); the checker is
  reproduced in §7 and should be reused.
* **Do not trust the Loom `mixin` AP's silence.** It only warns for a *name* it cannot remap
  (`Cannot remap jumpFromGround because it does not exists in any of the targets`), and warns nothing
  for a wrong descriptor.
* **Do not assume the two reference trees bracket 1.21.2.** They do not. See §2.
* **Do not run `./gradlew --stop`** in a shared environment. It kills the other agents' daemons.
  Clear the Loom cache by deleting the directories instead:
  `rm -rf .gradle/loom-cache/remapped_mods common/build/devlibs common/build/loom-cache fabric/build/loom-cache`
  (add `common/build/loom-cache` after adding a class to `:common`, or the loader module will compile
  against a stale remap and report `cannot find symbol` for a class that is right there).
* **Optional integrations have no 1.21.2 build** — see §4.

---

## 7. The mixin-target checker used here

`javap -p` on the mojmap jar, compared against every `method = "…"` string in the mixin sources.
Descriptors are compared parameter by parameter, with two allowances the raw `javap` output forces:
type variables (`shouldShowName(T, double)`) and primitives printed as words (`double` vs `D`).
Result for this tree: **39 targets checked, 0 problems**, plus 0 missing `@Shadow` members.

```python
JAR = ("/home/sanufsoii/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/"
       "1.21.2-loom.mappings.1_21_2.layered+hash.40545-v2/"
       "minecraft-merged-1.21.2-loom.mappings.1_21_2.layered+hash.40545-v2.jar")
PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
        "int": "I", "long": "J", "float": "F", "double": "D"}
TYPE_VAR = re.compile(r"^(L)?[A-Z](\$.*)?$")     # javap prints T, S, M where a descriptor has the erasure
# members(): parse `javap -p`, key methods by (name, descriptor) and fields by name
# descriptor(params): "," split, PRIM lookup, otherwise "L" + dotted.replace(".", "/") + ";"
# match(methods, name, desc): equal, or equal position by position with TYPE_VAR allowed
```

---

## 8. The published artifacts

```
0.1-mc1.21.2fabric-3.4.3.3.jar     <- fabric/build/libs/parcool-1.21.2-3.4.3.3-fabric.jar
0.1-mc1.21.2neoforge-3.4.3.3.jar   <- neoforge/build/libs/parcool-neoforge.jar
```

both copied to `/home/sanufsoii/ports/готовые порты/parcool/`.

Verified in the shipped jars, not only in the build tree: 350 classes in the Fabric jar, all
intermediary (`net/minecraft/class_*`), `parcool.accesswidener` in `v2 intermediary`, 28 mixins listed
in `parcool-common.mixins.json`, `fabric.mod.json` at `1.21.2-3.4.3.3` with
`minecraft: ~1.21.2` / `architectury: >=14.0.0`; 360 mojmap classes in the NeoForge jar,
`neoforge.mods.toml` at `3.4.3.3` with `[1.21.2,1.22)` / `[21.2,)`, the string-form recipe
ingredients, the `supported_formats` pack format, and one `ServiceLoader` binding per jar.

### A fresh checkout needs two invocations

`./gradlew build` on its own **fails on a truly clean checkout** (after `rm -rf build .gradle`):

```
A problem occurred configuring project ':fabric'.
> Failed to setup Minecraft, ... NoSuchFileException: common/build/libs/parcool-1.21.2-3.4.3.3.jar
```

`gradle build` matches the `build` task in *every* project, so `:fabric` has to be **configured**
before any task runs - and Architectury Loom resolves `:common`'s jar while configuring it. The root
`bootstrap` task cannot help, because task execution starts only after configuration finishes. This
is inherited from the 1.21.11 layout and is not specific to 1.21.2; the documented sequence is

```bash
./gradlew :common:build   # 9 s
./gradlew build           # 26 s
```

and both were verified from an empty `build/` + `.gradle/` for this port.

---

## 9. What was NOT verified

The build was accepted without launching the game, because the brief for this port forbids running
Minecraft. Concretely, these PROMPT phase-6 items are **untested**:

* the game starting on either loader, with the mod listed and no failed mod state;
* the ParCool attributes resolving on the first `Player#createAttributes` on a real NeoForge client
  (the `NeoForgeAttributes` split is inherited unchanged from the reference tree and the *reason* it
  exists — NeoForge freezing `BuiltInRegistries` before the mod constructors — was not re-measured
  for 21.2.1-beta);
* `grep "GL ERROR"` / `grep "Invalid key"` on a real log;
* rebinding and pressing every key in Options → Controls;
* right-click / Space / Ctrl still working on **Fabric** (the `restoreVanillaBindings` repair is
  present and the table it repairs was confirmed single-mapping by `javap`, but nothing exercised it);
* one action of each family, the stamina HUD, the settings screen;
* two clients seeing each other's animations;
* a Prism instance boot.

`./gradlew build` succeeds, `:common:checkCommonLoaderIndependence` passes, all 39 mixin targets and
every `@Shadow` resolve against the 1.21.2 jar, both distributables carry the right bytecode naming
(Fabric intermediary + `v2 intermediary` access widener + remapped mixin targets, NeoForge mojmap),
and the loader metadata, recipe format, sounds, translations, pack format and access transformers are
all re-derived for 1.21.2. That is as far as a no-launch acceptance can go.

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
