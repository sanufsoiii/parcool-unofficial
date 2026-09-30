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
0.1-mc1.21.2neoforge-3.4.3.3.jar   <- neoforge/build/libs/parcool.jar
```

both copied to `/home/sanufsoii/ports/готовые порты/parcool/`.

Note on the NeoForge name: `neoforge/build/libs/` also holds `parcool-neoforge.jar`, produced by the
hand-rolled `distJar` task. The two were compared entry by entry and are **identical except for
`META-INF/MANIFEST.MF`** (`distJar` adds `Implementation-Title/Version/Vendor`; the plain `jar`
task leaves the default manifest). Both carry all 361 classes, `neoforge.mods.toml`,
`parcool.accesswidener`, the mixin config and the `ServiceLoader` binding, because
`sourceSets.main.output` already includes `explodedCommon`. `parcool.jar` is what gets published,
matching what this port shipped before §10.

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

---

## 10. Three defects fixed after the port was built (orchestrator handoff, verified here)

All three were invisible to the build. `./gradlew build` was green before and after every one of
them, and none of them would have been caught by a compile. They are recorded here in the form the
next port needs them: what breaks in game, and the `javap` / real-codec line that proves it.

All `javap` evidence below is from the **mojmap** jar
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.2-loom.mappings.1_21_2.layered+hash.40545-v2/…jar`
— not from `minecraft-merged-…-sources.jar`, whose access widener has already been applied and which
therefore lies about member visibility.

### 10.1 Defect 1 — the four vanilla recipes did not parse at all (object-form ingredients)

**What was wrong.** `iron_zipline_hook.json`, `wooden_zipline_hook.json`, `zipline_rope.json` and
`reset_zipline_rope.json` all wrote their ingredients as JSON *objects*, `"C": {"item":
"minecraft:chain"}` / `"L": {"tag": "minecraft:logs"}` / `{"item": "parcool:zipline_rope"}`. On
1.21.2 that form does not exist.

**Why.** `javap -p -c net.minecraft.world.item.crafting.Ingredient`, `static {}` (offsets):

```
 48: getstatic     #376  // Field net/minecraft/core/registries/Registries.ITEM:Lnet/minecraft/resources/ResourceKey;
 51: invokestatic  #409  // Method net/minecraft/world/item/Item.CODEC:()Lcom/mojang/serialization/Codec;
 54: iconst_0
 55: invokestatic  #415  // Method net/minecraft/resources/HolderSetCodec.create:(…)Lcom/mojang/serialization/Codec;
 58: putstatic     #417  // Field NON_AIR_HOLDER_SET_CODEC
 61: getstatic     #417  // Field NON_AIR_HOLDER_SET_CODEC
 64: invokestatic  #423  // Method net/minecraft/util/ExtraCodecs.nonEmptyHolderSet:(…)Lcom/mojang/serialization/Codec;
 77: invokeinterface #433 // InterfaceMethod com/mojang/serialization/Codec.xmap:(…)
 82: putstatic     #434  // Field CODEC
```

and `Item.CODEC` itself (`javap -p -c net.minecraft.world.item.Item`, `static {}`):

```
  0: getstatic     #83   // Field net/minecraft/core/registries/BuiltInRegistries.ITEM:Lnet/minecraft/core/DefaultedRegistry;
  3: invokeinterface #590 // InterfaceMethod net/minecraft/core/DefaultedRegistry.holderByNameCodec:()Lcom/mojang/serialization/Codec;
 13: invokeinterface #606 // InterfaceMethod com/mojang/serialization/Codec.validate:(…)Lcom/mojang/serialization/Codec;
 18: putstatic     #608  // Field CODEC
```

Both branches are **strings**: `Item.CODEC` is `holderByNameCodec`, and `HolderSetCodec` reads a
`#`-prefixed string for a tag. No branch in the whole chain takes a JSON object.

**Measured, not inferred.** The real 1.21.2 `Ingredient.CODEC` was run against a bootstrapped
vanilla registry (`Bootstrap.bootStrap()` + `RegistryOps.create(JsonOps.INSTANCE,
RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))`):

```
"minecraft:chain"            -> OK -> [Reference{ResourceKey[minecraft:item / minecraft:chain]}]
["minecraft:chain"]          -> OK
"#minecraft:logs"            -> (tag branch taken; "Missing tag" only because the harness loads no tags — see below)
{"item":"minecraft:chain"}   -> FAIL 'Failed to parse either. First: Not a string: {"item":…};
                                       Second: Failed to parse either. First: Not a json array: …; Second: Not a string: …'
{"tag":"minecraft:logs"}     -> FAIL (same shape)
{"0":"minecraft:chain"}      -> FAIL (same shape)
```

And with the shipped files as they were:

```
-- iron_zipline_hook.json   DataResult.Error['… Map entry 'n' : Failed to parse either. First: Not a string: {"item":"minecraft:iron_nugget"}; …']
-- zipline_rope.json        DataResult.Error['… Map entry 'n' : Failed to parse either. …']
-- reset_zipline_rope.json  DataResult.Error['List is too short: 0, expected range [1-9]; …']
```

(`Unknown registry key … parcool:…` in those same messages is expected and is not a defect — the mod's
own items are not registered in a bare vanilla harness. Substituting a real item id leaves only the
ingredient error, which is why the shape above is unambiguous.)

**Control for the harness's tag limitation.** Decoding *all 1337* vanilla 1.21.2 recipes with the same
harness: **850 OK, 433 skipped (other recipe types), 0 failures of the `Not a string` /
`Not a JSON object` kind**, and every single failure is `Missing tag: <tag> in 'minecraft:item'` or
the empty-tag variant `List is too short: 0, expected range [1-9]`. One of them is
`data/minecraft/recipe/smoker.json`, which is structurally identical to this port's
`wooden_zipline_hook.json` (shaped, `"L": "#minecraft:logs"`) and produces the *same* single
`Missing tag: 'minecraft:logs'` message. So "Missing tag" is an artefact of a harness that never
loads tag files, not a property of our document. `data/minecraft/tags/item/logs.json` is present in
the 1.21.2 jar, so in game it resolves.

**Second, independent control.** All 1337 vanilla 1.21.2 recipes contain **zero** occurrences of
`"item":`. Vanilla's own tag usage is written `"L": "#minecraft:logs"`.

**Fix.** The four files now use the string form (`"minecraft:chain"`, `"#minecraft:logs"`,
`"parcool:zipline_rope"`). Re-running the harness after the fix:

```
-- iron_zipline_hook.json    OK -> DataResult.Error['Unknown registry key …: parcool:iron_zipline_hook']   (and with ids substituted: Success[ShapedRecipe])
-- zipline_rope.json         OK -> DataResult.Error['Unknown registry key …: parcool:zipline_rope']        (and with ids substituted: Success[ShapedRecipe])
-- reset_zipline_rope.json   OK -> DataResult.Error['… ']                                                (and with ids substituted: Success[ShapelessRecipe])
-- zipline_rope_dye.json     OK -> DataResult.Success      (unchanged; CustomRecipe.Serializer, no ingredients)
```

Only the harness's own missing-registry complaint is left.

**How it would have shown up in game.** No crash, green build, and the datapack loader writing
`Parsing error loading recipe parcool:iron_zipline_hook: …` and then silently dropping the recipe.
**Nothing in ParCool could have been crafted.** Exactly the class of defect a static build cannot
see, and the reason every one of these was re-checked against a live codec rather than by eye.

`zipline_rope_dye.json` was deliberately left alone: its `type` is the mod's own
`parcool:zipline_rope_dye`, and it declares no ingredients at all.

**Correction to PROMPT.md.** The break table there claimed the ingredients became object-form in
**1.21.5**. That is wrong; the seam is **1.21.1 → 1.21.2**. The same harness against the 1.21.1 jar
inverts completely:

```
== Ingredient.CODEC, isolated (1.21.1) ==
   "minecraft:chain"              -> FAIL 'Failed to parse either. First: Not a json array: "minecraft:chain"; Second: Not a JSON object: …'
   "#minecraft:logs"              -> FAIL (same shape)
   ["minecraft:chain"]            -> FAIL
   {"item":"minecraft:chain"}     -> OK
   {"tag":"minecraft:logs"}       -> OK
```

and `javap` agrees — on 1.21.1 `Ingredient$Value.CODEC` is
`Codec.xor(Ingredient$ItemValue.CODEC, Ingredient$TagValue.CODEC)`, where both branches are
`RecordCodecBuilder.create(…)` records, i.e. **objects wrapping a string**, not strings. So the
string form is correct on the whole 1.21.2 … 1.21.11 range, and the object form only ever worked on
1.21.1 and older. PROMPT.md's row and the closing paragraph were corrected accordingly.

### 10.2 Defect 2 — no `Properties#setId`, so registration would have thrown

**What was wrong.** All three items were built on a bare `new Item.Properties()` and both blocks on
`BlockBehaviour.Properties.of()…`, with no registry key.

**Why.** From 1.21.2 both properties classes can set the key, and both constructors consume it
immediately. `javap -p -c 'net.minecraft.world.item.Item$Properties'`:

```
234:  public Item$Properties setId(ResourceKey<Item>);
267:  protected String effectiveDescriptionId();
273:       8: ldc_w  #328  // String Item id not set
      11: invokestatic #334 // Method java/util/Objects.requireNonNull:(…)Ljava/lang/Object;
289:  public ResourceLocation effectiveModel();
295:       8: ldc_w  #328  // String Item id not set
```

`javap -p -c net.minecraft.world.item.Item`, constructor `Item(Item$Properties)`:

```
 19: invokevirtual #128  // Method Item$Properties.effectiveDescriptionId:()Ljava/lang/String;
 35: invokevirtual #140  // Method Item$Properties.effectiveModel:()Lnet/minecraft/resources/ResourceLocation;
```

`javap -p -c 'net.minecraft.world.level.block.state.BlockBehaviour$Properties'`:

```
441:  protected Optional<ResourceKey<LootTable>> effectiveDrops();
447:       8: ldc_w  #345  // String Block id not set
636:  public BlockBehaviour$Properties setId(ResourceKey<Block>);
653:  protected String effectiveDescriptionId();
659:       8: ldc_w  #345  // String Block id not set
```

`javap -p -c net.minecraft.world.level.block.state.BlockBehaviour`, constructor
`BlockBehaviour(BlockBehaviour$Properties)`:

```
 14: invokevirtual #102  // Method BlockBehaviour$Properties.effectiveDrops:()Ljava/util/Optional;
 22: invokevirtual #108  // Method BlockBehaviour$Properties.effectiveDescriptionId:()Ljava/lang/String;
```

So the failure is **inside the constructor**, not on first use: the handoff said the `BlockBehaviour`
constructor calls them, and it turns out `Item`'s does too. That makes the crash earlier and harder
than "sometime later when something asks for a name".

**Architectury does not compensate.** In `architectury-fabric-14.0.4.jar` there is not a single
occurrence of the string `setId` in any class file (checked by unpacking the jar and grepping
every `.class`), and `RegistrarImpl#register(ResourceLocation, Supplier)` is:

```
  0: aload_0
  1: getfield     #38  // Field delegate:Lnet/minecraft/class_2378;
  6: invokeinterface #84 // InterfaceMethod java/util/function/Supplier.get:()Ljava/lang/Object;
 11: invokestatic #90  // InterfaceMethod net/minecraft/class_2378.method_10230:(Registry;ResourceLocation;Object;)Object;
 17: invokevirtual #92 // Method delegate:(Lnet/minecraft/class_2960;)Ldev/architectury/…/RegistrySupplier;
```

It calls `Supplier.get()` (i.e. constructs the item) and then `Registry#register`, and never touches
the properties. Note this also means the item is constructed *inside* `DeferredRegister.register`,
so the `NullPointerException` lands during registry fill, as claimed. The same is true of
`architectury-fabric-16.1.4`.

**Fix**, in `Items#properties(String)` and `Blocks#key(String)`:

```java
ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, name))
```

The name is the item's/block's own id: `wooden_zipline_hook`, `iron_zipline_hook`, `zipline_rope`.
Both sides carry it in the shipped artifact — the NeoForge jar shows
`Item$Properties.setId:(Lnet/minecraft/resources/ResourceKey;)` and
`BlockBehaviour$Properties.setId:(Lnet/minecraft/resources/ResourceKey;)`; the Fabric jar shows the
intermediary forms `class_1792$class_1793.method_63686(Lnet/minecraft/class_5321;)` and
`class_4970$class_2251.method_63500(Lnet/minecraft/class_5321;)`.

**How it would have shown up in game.** `NullPointerException: Item id not set` (and `Block id not
set`) thrown out of `ParCool`'s registry callback on both loaders — the mod fails to load, every
world, every time. Not a soft failure.

**Correction to PROMPT.md.** PROMPT.md said nothing about `setId` anywhere. It now has a row for it
marked *mandatory from 1.21.2*. Worth repeating because it reads like a 1.21.4 nicety: the 1.21.2
jar already has `setId`, `Item`'s constructor already consumes it, and 1.21.1's
`Item$Properties` has neither (checked: `javap -p 'net.minecraft.world.item.Item$Properties'` on
the 1.21.1 jar contains no `setId` and no `effectiveDescriptionId`, and 1.21.1's
`BlockBehaviour` constructor calls neither `effectiveDrops` nor `effectiveDescriptionId`). The trap
is the 1.21.1 tree, which is both correct-for-its-version *and* the base this port was imported from.

### 10.3 Defect 3 — no `useBlockDescriptionPrefix()`, so the two hook names lost their translations

**What was wrong.** The two `BlockItem`s used plain `new Item.Properties()`. On 1.21.2 the
block-vs-item description prefix is a *property*, and it was left at its default.

**Why.** `javap -p 'net.minecraft.world.item.Item$Properties'`:

```
  private static final DependantName<Item, String> BLOCK_DESCRIPTION_ID;
  private static final DependantName<Item, String> ITEM_DESCRIPTION_ID;
```

`Item$Properties()` puts `ITEM_DESCRIPTION_ID` into the `descriptionId` field, and
`useBlockDescriptionPrefix()` replaces it with `BLOCK_DESCRIPTION_ID`:

```
  public Item$Properties useBlockDescriptionPrefix();
      0: getstatic  #323  // Field BLOCK_DESCRIPTION_ID:Lnet/minecraft/resources/DependantName;
      4: putfield        // Field descriptionId
```

`Item#getDescriptionId()` is now `final` (`public final java.lang.String getDescriptionId();`, field
`protected final java.lang.String descriptionId;`), and `javap -p net.minecraft.world.item.BlockItem`
shows **no** `getDescriptionId` override — the 1.21.1 delegate is gone. So without the flag the
description id resolves as `item.parcool.<id>`, while the shipped lang files only ever define
`block.parcool.wooden_zipline_hook` / `block.parcool.iron_zipline_hook` — and only 4 of the 11 lang
files do (`en_us`, `ja_jp`, `zh_cn`, `zh_tw`); the other 7 fall back to English anyway. No lang file
in the mod defines an `item.parcool.*` key for either hook.

**Fix.** `Items#blockItemProperties(String)` = `properties(name).useBlockDescriptionPrefix()`, used
for both `BlockItem`s.

**How it would have shown up in game.** No crash, no log line: the two hooks would simply show up
untranslated — `item.parcool.wooden_zipline_hook` instead of "Wooden Zipline Hook" — in the
inventory, the creative tab, tooltips and the recipe book, in every language. The kind of defect that
survives review because nothing looks broken.

### 10.4 Warnings for the next port

* **`setId` and `useBlockDescriptionPrefix()` are mandatory on the whole 1.21.2+ branch**, not a
  1.21.4 nicety. On 1.21.2 the item/block *constructors* already consume the key. Both are invisible
  to the compiler and, in the `useBlockDescriptionPrefix` case, to the log.
* **Comment every one of the three calls.** `setId(ResourceKey.create(Registries.ITEM,
  ResourceLocation.fromNamespaceAndPath(MOD_ID, name)))` sits two lines below a `register("name", …)`
  that appears to know the same string, and the next reader will delete it as redundant. The javadoc
  on `Items#properties` / `Items#blockItemProperties` / `Blocks#key` explains why; keep it in sync if
  you re-derive these.
* **The string ingredient form is correct from 1.21.2 all the way to 1.21.11.** The object form is
  1.21.1-and-older only. If you import from the 1.21.1 tree you will inherit four broken recipes and
  have to rewrite them; that tree is a known source of this defect.
* **The 1.21.1 tree (`parcool-Architectury-API-1.21.1`) has a latent bug that this port inherited
  and then fixed, and which is still present there.** Its `Ingredient$Value.CODEC` is
  `Codec.xor(ItemValue.CODEC, TagValue.CODEC)` — both branches `RecordCodecBuilder` records over a
  string, i.e. object form — and its `data/parcool/recipe/*.json` write exactly that object form, which
  is correct for 1.21.1 and wrong for anything newer. It also has no `setId` anywhere, correct for
  1.21.1 and fatal from 1.21.2 on. That tree is read-only for this work, so it was not fixed; anyone
  publishing it should know the bug is a property of the version, not a mistake in the port.
* **`"category": "misc"` stays.** `CraftingBookCategory.CODEC.fieldOf("category")
  .orElse(CraftingBookCategory.MISC)` — optional, and the explicit field is exactly the codec's own
  default (see the section above). The harness also decodes all five files with the field present.
* **`assets/parcool/models/item/*.json` is the right folder here.** The 1.21.2 jar has **1833**
  files under `assets/minecraft/models/item/` and **0** under `assets/minecraft/items/`; the `items/`
  folder arrives in 1.21.4. Do not "modernise" this one.
* **`minecraft:chain` is the right id here.** `assets/minecraft/models/item/chain.json` and
  `assets/minecraft/models/block/chain.json` exist in the 1.21.2 jar and there is no `iron_chain`
  anywhere in it.
* **`pack.mcmeta` was re-verified and left alone**, as the table predicted.
  `SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)` reports **42** and
  `getPackVersion(PackType.SERVER_DATA)` reports **57** on this build, and
  `PackMetadataSection.CODEC` (which has a
  `Codec.lenientOptionalFieldOf("supported_formats")` branch, so the field *is* supported on 1.21.2)
  decodes the shipped file to `packFormat=34 supportedFormats=Optional[[34, 57]]`. 34..57 covers
  both checks. No change.
* **`KeyBindings#restoreVanillaBindings` was not touched** — still called from
  `KeyBindings#register` (line 290) and driven by `KeyRecorder#onClientTick` (line 35), and still
  needed on 1.21.2 because `KeyMapping.MAP` is `Map<Key, KeyMapping>` there.

### 10.5 Still not verified

The fixes above are proven at the bytecode and codec level and by the contents of both
distributables. **The game was not launched** — no `:fabric:runClient`, no `:neoforge:runClient`, no
server — because the brief for this port forbids it. In particular, "the recipes now appear in the
recipe book and craft" and "the hooks show a translated name" are *expected* from the evidence, not
observed.

## 11. Defect 4 (found by running the game): the payload was decoded in a released buffer

Found by launching a client, not by building. Both loaders, both files:
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

One line, identical on Fabric and NeoForge, at `lambda$register$0`:

```java
(buf, context) -> context.queue(() -> handler.accept(erased.decode((RegistryFriendlyByteBuf) buf), context))
```

`NetworkManager.registerReceiver` hands the lambda a **raw** buffer on the network thread and
releases it as soon as the lambda returns. That is not an inference from the log — it is what
Architectury's own source does. `dev.architectury.impl.NetworkAggregator#registerReceiver(Side,
ResourceLocation, List, NetworkReceiver)` (architectury 14.0.4, the version this port pins):

```java
registerC2SReceiver(type, BufCustomPacketPayload.streamCodec(type), packetTransformers, (value, context) -> {
    class_9129 buf = new class_9129(Unpooled.wrappedBuffer(value.payload()), context.registryAccess());
    receiver.receive(buf, context);   // <- ParCool's lambda runs here, buf is still alive
    buf.release();                    // <- and is freed the moment the lambda returns
});
```

`PacketContext#queue` is `taskQueue.execute(runnable)` — it **defers** the runnable. On the server
that is the main-thread task queue, so the runnable body runs strictly *after* the lambda returned
and *after* `buf.release()`. The decode therefore reads a `ByteBuf` whose `refCnt` is already 0,
which is exactly what `VarLong.read` -> `AbstractByteBuf.readByte` checks. The first ParCool packet
in each direction kills the server task and the limitation snapshot never arrives, so no action can
start: the "almost nothing works" report.

`queue` is needed for **thread safety of the handler**, not for decoding. The handler touches the
player, the level and ParCool's own state, all of which are main-thread-only; the decode is pure
buffer arithmetic and has no reason to be off the network thread.

### The fix

Decode first, on the network thread, while the buffer is still alive; queue only the handler.

```java
(buf, context) -> {
    T payload = erased.decode((RegistryFriendlyByteBuf) buf);
    context.queue(() -> handler.accept(payload, context));
}
```

The comment in the source repeats this causal chain on purpose. Without it the next reader sees
"decode is just reading a local, hoist it back into the lambda" and puts the bug back — the code is
byte-identical in intent and only the statement order differs.

### Proof in the built artifact

`javap -p -c` on the class inside both distributables shows the decode at offset 2 and the queue at
offset 22 of the same synthetic method, and a second synthetic method whose entire body is a single
`BiConsumer.accept` — i.e. the queued runnable no longer touches the buffer:

```
private static void lambda$register$1(StreamCodec, BiConsumer, RegistryFriendlyByteBuf, PacketContext);
   2: invokeinterface  StreamCodec.decode:(Ljava/lang/Object;)Ljava/lang/Object;
  10: astore        4                      <- payload held in a local
  17: invokedynamic  run:(BiConsumer;CustomPacketPayload;PacketContext;)Runnable
  22: invokeinterface  PacketContext.queue:(Ljava/lang/Runnable;)V

private static void lambda$register$0(BiConsumer, CustomPacketPayload, PacketContext);
   2: invokeinterface  BiConsumer.accept:(Ljava/lang/Object;Ljava/lang/Object;)V
```

### This class of defect is invisible to the compiler and only a live client catches it

**`./gradlew build` is green on the broken code and stays green on the fixed code.** There is nothing
to compile wrong: `buf` is a live `RegistryFriendlyByteBuf` parameter, `decode` takes it, and
capturing it in a nested lambda is perfectly legal Java. The buffer's refcount is a *runtime* netty
property, and the window in which it is valid is a *lifetime* property of Architectury's
`registerReceiver` contract — neither is expressible in the type system.

It is equally invisible to a test: without a client that has **joined a world**, no ParCool packet
ever travels, the receiver lambda is never invoked, and the released-buffer read never happens. The
port's whole verification story up to this point — `checkCommonLoaderIndependence`, `:common:build`,
`./gradlew build`, mixin-target checks against the real jars, unzipping the distributables — passes
just as happily on code that dies on the first packet.

So the only oracle that found this class of defect is a client launched by a human, entering a world,
with a server on the other end. Build output, compiler warnings, jar contents and mixin validation
are all necessary and none of them is sufficient.

### What was NOT verified

**The game was not launched for this fix.** No `:fabric:runClient`, no `:neoforge:runClient`, no
dedicated server. The evidence above is: Architectury's own source for the buffer contract,
`javap` on the built class, and a green build. "The exception no longer appears" and "actions can
now start" are *expected* from that evidence, not observed.

### Artifacts for this fix

```
0.1-mc1.21.2fabric-3.4.3.3.jar     sha256 95fe10c25f35df3b177cb5e53c8b4ab9fd2cd57709cb0edf9d398de0cf4c2423
0.1-mc1.21.2neoforge-3.4.3.3.jar   sha256 c7f63d5b24c01c360d83ac45fa4848fddecfe125923ac10c03e9fb02e27f11eb
```

Both hashes differ from the pair published before this fix (`67a43232…` / `48eda650…`) — this is
code, so it must change. The game was not launched to validate it.

## LivingRendererMixin and the Loom cache (found by launching the client)

A live Fabric client on 1.21.2 logged this at boot, every single time:

```
[FabricLoader/Mixin] parcool-common.mixins.json:client.LivingRendererMixin from mod parcool:
  Super class 'net.minecraft.client.renderer.entity.LivingEntityRenderer' of
  client.LivingRendererMixin was not found in the hierarchy of target class
  'net/minecraft/client/renderer/entity/LivingEntityRenderer'
  at MixinInfo$SubType$Standard.validate(MixinInfo.java:593)
```

Two separate defects were tangled here and only one of them is fully understood.

**1. The mixin extended its own target.** The class was declared
`abstract class LivingRendererMixin<T, S, M> extends LivingEntityRenderer<T, S, M>` with a copy
constructor, on the (incorrect) assumption that the mixin must re-declare the target's generics
for its handler parameter to resolve. The generics were never needed: `shouldShowName` erases
to `(LivingEntity, double)`, so the handler takes `LivingEntity` directly. The mixin now declares
no supertype and no constructor, which is the shape all eight other ports use.

**Honest caveat:** I did not isolate whether this alone caused the message. The `extends` was
removed and the Loom cache purged in the same step, and the client came up clean. Mixin's
`SubType$Standard.validate` walks the target's ancestor chain, and a target is never in its own
ancestor list, so the theory holds - but treat it as unproven.

**2. A stale Loom cache masked the fix and is the more dangerous of the two.** Loom keeps a
remapped copy of `:common` under `.gradle/loom-cache/remapped_mods/.../common-<hash>.jar` and
feeds it to the dev client. Deleting `fabric/build/loom-cache` and `common/build/loom-cache` is
**not** enough - `.gradle/loom-cache/remapped_mods` is a separate tree and was still holding a
copy compiled before the fix. The symptom is a source file that is provably correct on disk and
a running client that behaves as if it were not. Verify with:

```bash
cd common/build/classes/java/main && javap -p com/alrex/parcool/mixin/client/LivingRendererMixin.class
for j in $(find ../../../../.. -path '*remapped_mods*' -name 'common-*.jar'); do
  unzip -p "$j" com/alrex/parcool/mixin/client/LivingRendererMixin.class > /tmp/k.class
  javap -p /tmp/k.class | sed -n 2p
done
```

Full purge, with no `--gradlew --stop` (which would kill neighbouring projects' daemons):

```bash
rm -rf .gradle/loom-cache fabric/build/loom-cache common/build/loom-cache \
       common/build/devlibs neoforge/build/explodedCommon
```

Note that Mixin reports this at ERROR and the boot *continues*: the mod loads, the window opens,
nothing looks broken, and the mixin is simply dead. In 1.21.2 the dead mixin means HideInBlock
and WallSlide keep drawing other players' name tags. A build check cannot see this, and neither
can a jar-structure check.

Sweep of the sibling ports at the same moment found the same stale-cache trap in 1.21.5
(`Blocks.java` newer than the cache) and 1.21.6 (five non-mixin sources newer). Both purged.
Check your own port with `find .gradle/loom-cache/remapped_mods -name 'common-*.jar'` and
compare against `find common/src/main/java -newer <that jar>`.
