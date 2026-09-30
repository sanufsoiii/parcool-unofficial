# NOTES — ParCool Architectury port to Minecraft 1.21.9

Working notes for this port. Findings, dead ends, decisions the code cannot explain on its own, and
upstream bugs. Read-only reference trees: 1.21.11 (base) and 1.21.1 (second worked example).

---

## Phase 0 — orientation

`README.md` / `BUILDING.md` / the three `build.gradle` files of both reference trees were read, plus
1.21.11's git history (three commits: import, packaging/move-tick/animator fixes, and the
move-recursion guard moved out of the mixin into a plain mod class).

The base tree was copied verbatim: root + `common` (loom) + `fabric` (loom) + `neoforge`
(ModDevGradle), 250 Java files, one `parcool-common.mixins.json`, one `parcool.accesswidener`, one
`META-INF/accesstransformer.cfg`.

---

## Phase 1 — toolchain (resolved, not guessed)

Every number below was read out of a metadata endpoint on 2026-09-30.

| Property | Value | Where it came from |
|---|---|---|
| `minecraft_version` | `1.21.9` | target; `launchermeta.mojang.com/mc/game/version_manifest_v2.json` (released 2025-09-30) |
| `neo_version` | `21.9.16-beta` | last `21.9.*` entry in `maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`. **There is no non-beta NeoForge build for 1.21.9** — every `21.9.x` release carries the `-beta` suffix, and the next entry in the list is `21.11.42` |
| `loader_version` | `0.19.5` | `meta.fabricmc.net/v2/versions/loader/1.21.9` → `loader[0].loader.version` |
| `fabric_api_version` | `0.134.1+1.21.9` | highest `<version>*+1.21.9</version>` in `maven.fabricmc.net/.../fabric-api/maven-metadata.xml` (the line jumps from 0.134.1 straight to `0.136.0+1.21.10`) |
| `architectury_api_version` | **`18.0.5`** (was `18.0.8` in phase 1) | see below, and *Phase 8* for why 18.0.8 cannot boot 1.21.9 at all |
| `dev.architectury.loom` | `1.17.493` | highest published version in `maven.architectury.dev/dev/architectury/architectury-loom/maven-metadata.xml` |
| `architectury-plugin` | `3.5.170` | highest in `architectury-plugin.gradle.plugin/maven-metadata.xml` |
| `net.neoforged.moddev` | `2.0.148` | highest in `net.neoforged.moddev.gradle.plugin/maven-metadata.xml` (2.0.147 → 2.0.148 → end) |
| Gradle wrapper | `9.4.1` | unchanged from the base tree; already in `~/.gradle/wrapper/dists` |
| Java | 21 | `version.json` of the 1.21.9 client: `java_version: 21` |

### Architectury API for 1.21.9 — the brief's guess was wrong

The handoff guessed that "19.0.x may be the 1.21.9 line rather than 1.21.11-only". It is not.
Downloaded every candidate jar in the 16.x–19.x range and read `depends.minecraft` out of its own
`fabric.mod.json`:

| architectury-fabric | `depends.minecraft` | accepts 1.21.9 |
|---|---|---|
| 16.0.3, 16.1.4 | `~1.21.4-` | no |
| 17.0.3, 17.0.4 | `~1.21.6~` | no |
| 17.0.6 | `~1.21.6` | no |
| 17.0.8, 18.0.2 – 18.0.8 | `~1.21.7` (`>=1.21.7 <1.22.0`) | **yes** |
| 19.0.1 | `~1.21.11` | no — `1.21.9 < 1.21.11` |

So **19.0.1 refuses 1.21.9 outright**, and 18.0.8 is the newest line whose version predicate accepts
it. Note the gap: Architectury published nothing between `18.0.8` (1.21.7 line) and `19.0.1`
(1.21.11), so 1.21.8 / 1.21.9 / 1.21.10 all fall into the `~1.21.7` window.

`architectury-neoforge` cannot be used to discriminate: **every** build in 16.x–19.x declares the
same `minecraft [1.21.4,)` / `neoforge [21.0.110-beta,)`.

**Open risk, not resolved:** 18.0.8 was *compiled* against MC 1.21.7, and a version predicate that
covers 1.21.9 does not prove its bytecode survives there. Architectury API's mixins touch
`KeyMapping`, `BlockEntityType`, `ResourceLocation`, … ; if 1.21.8–1.21.10 changed any of the members
its mixins target, the result is a mixin apply failure at boot on Fabric. The compiler accepts it
(:common:compileJava and :fabric:compileJava both pass), and the NeoForge side is unaffected because
its version range is wide. This cannot be closed without launching the game, which phase 6 of this
task forbids — see "Not verified" below.

### Build facts carried over from the finished ports

* `./gradlew build` in one invocation does **not** work on a clean checkout: Architectury Loom resolves
  the `:common` project dependency while it *configures* `:fabric`, so `:common`'s jar must exist
  first. Working sequence: `./gradlew :common:build && ./gradlew build`. Inherited from 1.21.11,
  documented in `BUILDING.md`, **not** "fixed".
* ModDevGradle on Gradle 9 does not wire up Gradle's `RepositoriesPlugin`, so Mojang's own
  `libraries.minecraft.net` had to be added explicitly to `neoforge/build.gradle`. The 1.21.11 tree
  does not have it. Recorded in the file with the symptom (`Could not find com.mojang:jtracy`).
* `minecraft-merged-*-sources.jar` in the Loom cache is generated *with the project's access widener
  already applied* and therefore lies about member visibility. Every access question was answered
  from `javap` against the plain mojmap named jar instead.

---

## Phase 2/3 — version deltas, and how each was decided

`javap` against the mojmap named jars of **1.21.7 / 1.21.9 / 1.21.11** side by side, plus
vineflower-decompiled 1.21.9 sources where a signature was not enough. 1.21.9 is genuinely a mix: it
keeps 1.21.7's renderer and key-mapping shape but already has 1.21.11's render-state rework, which is
why the version table in the brief ("1.21.1 old side / 1.21.11 new side") is misleading here.

| Area | 1.21.9 actually is | Taken from |
|---|---|---|
| `ResourceLocation` vs `Identifier` | **`ResourceLocation`** — `net.minecraft.resources.Identifier` does not exist. The rename is 1.21.11-only | 1.21.11 tree, `Identifier` → `ResourceLocation` in 32 files |
| `PlayerModel` package | **`net.minecraft.client.model.PlayerModel`** (1.21.11 moved it to `.model.player`) | 1.21.11 source, import fixed |
| Render types | **`RenderType` + `RenderStateShard` + `CompositeState`**, no `client.renderer.rendertype` package at all | 1.21.7 `RenderTypes.java` rewritten by hand, plus the 1.21.7 AW/AT block |
| `RenderPipeline` | already exists, `MATRICES_FOG_SNIPPET`/`PIPELINES_BY_LOCATION` still private | 1.21.7 |
| Entity rendering | **new side**: `AvatarRenderState`, `AvatarRenderer`, `extractRenderState`/`submit` | 1.21.11 |
| `KeyMapping.Category` | **new side**: record, `MAP` is `Map<Key, List<KeyMapping>>` | 1.21.11 |
| one-mapping-per-key conflict | **gone** — multi-mapping table, so `restoreVanillaBindings` is correctly absent | 1.21.11 (no action) |
| `Screen#resize` | **old side**: `(Minecraft, int, int)`; 1.21.11 dropped the `Minecraft` parameter | 1.21.7 form |
| `Screen#keyPressed` / `mouseClicked` | **new side**: `KeyEvent` / `MouseButtonEvent` | 1.21.11 |
| `Camera` | **old side**: `setup(BlockGetter,…)`, `getYRot()/getXRot()`, `getUpVector()/getLeftVector()` returning live `Vector3f` | 1.21.1 `CameraAnglesMixin`, rewritten |
| `SoundInstance` | **old side**: `getLocation()`, `getIdentifier()` does not exist | 1.21.7 one-liner |
| `Commands.LEVEL_GAMEMASTERS` | **old side**: an `int` + `CommandSourceStack#hasPermission(int)`; the `PermissionCheck`/`PermissionSet` rework is 1.21.11-only | 1.21.7, 8 call sites in 2 files |
| `BlockEntityType` | no `Builder`, private ctor, private static `register` — same as 1.21.7 **and** 1.21.11 | 1.21.11 (`BlockEntityTypeInvoker` + platform seam) kept; both work, the invoker needs no AW |
| `ArgumentTypeInfos#register` | **old side**: still `private static` (1.21.11 made it public) | needs the 1.21.7 AW entry — see the bug list |
| `LevelResource#<init>` | **old side**: still `private`; 1.21.11 made it public | not needed on NeoForge (public there); not needed on Fabric either since… see bug list |
| `Player#canInteractWithEntity` | **present** on 1.21.9 (removed in 1.21.10+), so `getVisibilityPercent` is the only hook and both exist — the 1.21.11 choice is still correct | 1.21.11 |
| `Entity#hurt` split | **new side**: `final void hurt` + `hurtOrSimulate`/`hurtServer` exist already on 1.21.7 | 1.21.11 |
| `jumpFromGround` on `LivingEntity` | new side (as in 1.21.7) | 1.21.11 `LivingEntityJumpMixin` |
| `Player#causeExtraKnockback` | **absent** — 1.21.11-only extraction; `setSprinting(false)` is still inline in `Player#attack` | 1.21.7 `@WrapWithCondition` target |
| `pack.mcmeta` | `PackFormat` record, `RESOURCE_PACK_FORMAT=69`, `DATA_PACK_FORMAT=88`, `lastPreMinorVersion` = 64 (client) / 81 (server) | new shape, see below |
| Recipe ingredients | string form still accepted (`Ingredient.CODEC` → `HolderSetCodec`) | 1.21.7 strings kept |
| `Item.Properties#setId` | required, `useBlockDescriptionPrefix()` present | 1.21.7 `Items.java` (adds `blockItemProperties`) |
| Entity/BlockEntity save | **new side**: `ValueInput`/`ValueOutput` | 1.21.11 |
| Attribute holder lookup | `Registry#get` returns `Optional` — the brief's "1.21.1 returns the value" row is **wrong for all three** versions checked | n/a |
| `minecraft:chain` vs `iron_chain` | 1.21.9 has `IRON_CHAIN` (renamed from 1.21.7's `CHAIN`) | see bug list |

### `pack.mcmeta`, decided by executing the codec

The shape matters, so it was tested rather than guessed. `PackMetadataSection.forPackType(...)`
was called directly against the 1.21.9 classes with a hand-written classpath and both real format
numbers from `version.json` (`resource_major: 69`, `data_major: 88`):

```
pack_format 88 / min 88 / max 88 / supported_formats [88,88]
  CLIENT_RESOURCES: PARSE FAILED: "key supported_formats is deprecated starting from pack format 65"
  SERVER_DATA:      PARSE FAILED: "key supported_formats is deprecated starting from pack format 82"

pack_format 88 / min 88 / max 88
  CLIENT_RESOURCES: range=[88.0, 88.*] current=69.0 -> TOO_NEW  *** NOT COMPATIBLE ***
  SERVER_DATA:      range=[88.0, 88.*] current=88.0 -> COMPATIBLE

pack_format 88 / min 64 / max 88 / supported_formats [64,88]      <- shipped
  CLIENT_RESOURCES: range=[64.0, 88.*] current=69.0 -> COMPATIBLE
  SERVER_DATA:      range=[64.0, 88.*] current=88.0 -> COMPATIBLE
```

So on 1.21.9 `supported_formats` must be present *and* `min_format` must be at or below
`lastPreMinorVersion` (64 / 81), and the declared range has to straddle both 69 and 88. The 1.21.11
tree's `pack_format: 81 / supported_formats: [81, 81]` would have been **TOO_OLD** on 1.21.9.
The harness is `/tmp/opencode/packtest/PackTest.java` (it needs
`SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` and `SharedConstants` steals
`System.out`, hence the private `OUT` stream).

### Optional integrations — none of them has a 1.21.9 build

Checked the full CurseForge file list per project (the public `www.curseforge.com/api/v1/mods/<id>/files`
endpoint; `api.curseforge.com` needs a key and returns 403).

| Mod | Compiled against | Why |
|---|---|---|
| Paraglider | `6739612` = 21.5.2 (MC 1.21.5) | 1.21.9 has no build. 21.5.2's API is spelled with `ResourceLocation`; the 21.11.0-beta.6 build uses `Identifier` and would not compile here |
| ShoulderSurfing | `6496668` = 1.21.1-4.11.0 | the 5.x line publishes only 1.21.1 and 1.21.11, and 5.x changed `IShoulderSurfingPlugin#register` to take an `IEventBus` instead of `IShoulderSurfingRegistrar` |
| BetterThirdPerson | `6455836` = 1.21.5-1.9.0 | newest NeoForge build at all; CurseForge lists it for 1.21.5–1.21.8 |
| EpicFight | `7489617` = 21.15.1-mc1.21.1 | its 1.21.1 line is still the latest release |

**Therefore all four integrations are compile-only and inert at runtime on 1.21.9**: no published
build declares this Minecraft version, so a 1.21.9 client cannot install one. That is stated in
README.md. Paraglider is the only one also pulled onto the runtime classpath, because its
`@ParagliderPlugin` plugin class has to be discoverable by Paraglider's own loader.

---

## Bugs found and fixed

1. **`ConfigSpec#persist()` wrote nothing** (inherited from the 1.21.11 base). It only set a `dirty`
   flag that nothing ever read, and the settings screens reach the file *only* through it, so every
   change made in the GUI was dropped and the config reverted on next launch. Restored the 1.21.1
   behaviour (write on `persist()`), keeping the flag. Verified the write volume is fine: the screens
   call `save()` on tab switch / screen close, not per frame.
2. **`BufferUtil.ensureRoom` was `static` over uninitialised state, and `putVector3i`/`putVec3` had
   their checks deleted** (inherited from the base). The static reference was set once in the
   constructor and never per action, so the check described the wrong buffer and threw nothing, while
   the two widest writers were unchecked — i.e. exactly the silent overflow the method exists to
   prevent. Took the 1.21.1 implementation (instance method, checks restored) and documented why.
3. **`RenderTypes` registered its pipelines on the first frame.** `ShaderManager#apply` precompiles
   `RenderPipelines#getStaticPipelines()` during the resource reload, long before the rope renderer
   exists, and aborts the game on the first pipeline that fails to compile. Added the explicit
   `RenderTypes.register()` and called it from `Renderers.register()`.
4. **`RenderType#create` and friends were reachable only through another mod's access widener.**
   `:common` compiled against a Loom jar whose access wideners are the union of every dependency's, and
   both **Architectury API** (`architectury.accessWidener`, `transitive-accessible`) and
   **fabric-transitive-access-wideners-v1** widen exactly the members `client/renderer/RenderTypes`
   needs. The mod's own `parcool.accesswidener` mentioned none of them, so the dependency was
   invisible: blank the AW and `:common` still compiles, which is how it was found. All five render
   members are now declared in `parcool.accesswidener`, so the mod is self-sufficient rather than
   relying on the internals of two other mods.
   (`LevelResource`'s private constructor is in the same category — Architectury widens it — but it is
   not needed here: `Limitations` is compiled against the *NeoForge* jar for `:neoforge` and against
   the AW-applied Loom jar for `:common`, and the member is only reached from `common/…/Limitations`,
   which the Architectury widener covers.)
5. **All five `data/parcool/recipe/*.json` were checked against the real 1.21.9 recipe codecs — and
   the handoff's "missing mandatory `category`" claim does NOT hold for this version, so nothing was
   changed.** The claim is based on `Codec.fieldOf("category")`, which is what `javap -c` shows on all
   three serializers. The decompiled 1.21.9 source shows what follows that call, and it is
   `MapCodec.orElse(CraftingBookCategory.MISC)`:

       ShapedRecipe.java:108      CraftingBookCategory.CODEC.fieldOf("category").orElse(MISC)…
       ShapelessRecipe.java:87    (same)
       CustomRecipe.java:39       (same)
       TransmuteRecipe.java:116, AbstractCookingRecipe.java:75  (same)

   So `category` is **optional** on 1.21.9 and defaults to `misc`. Confirmed by execution: all five
   shipped files decode successfully through `ShapedRecipe.Serializer#codec()` /
   `ShapelessRecipe.Serializer#codec()` / `CustomRecipe.Serializer#codec()` *both with and without*
   the field (`/tmp/opencode/recipecheck/RecipeTest.java`, with vanilla's own `iron_chain.json` and
   `white_dye.json` as controls that decode through the same path). The five files are therefore left
   exactly as the 1.21.11 tree has them. If the claim is meant for some other version, it is not this
   one.
6. **`PlayerMixin`'s `@WrapWithCondition` targeted a method that does not exist on 1.21.9.**
   `Player#causeExtraKnockback` is a 1.21.11 extraction; on 1.21.9 the `setSprinting(false)` is still
   inline in `Player#attack` (verified in the 1.21.9 bytecode, and the method is absent from
   `javap`). Under `defaultRequire: 1` a missing target is a hard boot failure. Retargeted to
   `attack`, as in 1.21.7.
7. **`minecraft:iron_chain` in `zipline_rope.json` is correct for 1.21.9 — the brief's warning was
   aimed at the wrong version.** 1.21.9 is *after* the rename: `javap` on the 1.21.9 jar shows
   `Items.IRON_CHAIN` and `Blocks.IRON_CHAIN`, the item model is
   `assets/minecraft/models/item/iron_chain.json`, and vanilla's own `data/minecraft/recipe/iron_chain.json`
   builds it from `iron_ingot` + `iron_nugget`. There is no `minecraft:chain` in 1.21.9. So the 1.21.11
   tree's value is kept unchanged; the rule is "not 1.21.2–1.21.7, which is where `chain` is correct".
8. **NeoForge's AT listed eight entries that are already public on 21.9.16-beta.** Trimmed to the two
   that are still private there (`ArgumentTypeInfos#register`, `RenderPipelines#PIPELINES_BY_LOCATION`),
   each verified against `neoforge/build/moddev/artifacts/neoforge-21.9.16-beta.jar`, with the
   measured visibility of every other member recorded in the file.
9. **`ClientWorldMixin`/`hideInBlock`/`ZiplineHookTileEntity` etc. comments** said "1.21.11" where they
   described behaviour that is 1.21.9's. Reworded, keeping the five places where a 1.21.11 comparison
   is the point of the sentence.

## Handoff claims checked and found NOT to hold on 1.21.9

Recorded so the next port does not re-derive them. Each was tested, not inferred.

* **"All five recipes are missing a mandatory `category`."** False for 1.21.9 — see bug 5 above.
* **"`minecraft:iron_chain` does not exist on 1.21.9."** False — it does; see bug 7 above.
* **"Architectury 19.0.x may be the 1.21.9 line."** False — 19.0.1 declares `~1.21.11`.
* **"`Registry#get` returns the value on 1.21.1 and an `Optional` on 1.21.11."** It returns an
  `Optional` on **all three** (1.21.1, 1.21.9, 1.21.11) — the row in the brief's table is simply wrong.
* **"`Item$Properties#setId` and `BlockItem#useBlockDescriptionPrefix` are missing from both reference
  trees."** `setId` is present in both; `useBlockDescriptionPrefix` is present in 1.21.7's `Items.java`
  and was carried over here.
* **"`Limitations` filename validation vanished."** Present in all three trees.

## Bugs looked at and confirmed already correct in the base

* `Limitations` filename/UUID validation is present in all three trees (the brief's "vanished" claim
  does not apply to 1.21.11 or 1.21.9).
* `ZiplineRopeEntity` writes six distinct NBT keys (`Tile1_X/Y/Z`, `Tile2_X/Y/Z`).
* `KeyBindings#isDown`/`isMetaKeyDown` reject an unbound keysym before `glfwGetKey`.
* `Item.Properties#setId` is set explicitly for every item (Architectury's `DeferredRegister` does not).
* `~25` unused imports are upstream in both reference trees; left alone as instructed.
* `Item.Properties#useBlockDescriptionPrefix()` — the brief lists this as missing from both reference
  trees; it *is* present in 1.21.7's `Items.java` (added there), and 1.21.9 needed the same, so it
  was taken.

## Not verified (no game was launched)

Phase 6 of the brief asks for a dev-run on both loaders and a Prism instance. The task instructions
for this port forbid launching Minecraft of any kind, so the following remain untested and are the
most likely places for a remaining defect:

* **Both loaders booting.** The strongest proxy used was a three-stage static verification, all
  passing: (a) every mixin target in the source resolves in the 1.21.9 mojmap jar, including exact
  descriptors; (b) every `@Inject`/`@Redirect` **handler arity** matches its target's parameter count
  — the failure mode the brief warns about, which `javap` on the *target* cannot catch; (c) every
  remapped target inside the **shipped** Fabric jar resolves in the 1.21.9 *intermediary* jar, since
  Loom's static remap rewrites targets into the bytecode and the jar ships no refmap to fix a
  mistake at runtime. Both verifiers are self-tested against a deliberately broken target.
* **Architectury API's own mixins applying on 1.21.9** (the open risk from phase 1). **Resolved
  later, from a real run** — see *Phase 8* below: the `JAVA_16` / class-version-65 warnings are
  cosmetic, and the log shows the mixins being applied by name. The part that is *still* open is the
  behaviour behind them, and the version moved from 18.0.8 to 18.0.5, which is itself a source of
  unverified behaviour.
* **Runtime behaviour of anything behind a `defaultRequire: 1` mixin**, and the visual result of the
  camera-roll path.
* **That a ParCool packet round-trips.** A client did reach a world in this port's own run log and
  the network was still found, by reading Architectury's source, to decode a released buffer — the
  fix is in and proved in the bytecode, but no session has yet delivered a ParCool payload to
  confirm it. See *The decode ran on a released buffer* below.
* **The four optional integrations**, which cannot be exercised at all on 1.21.9 (no build exists).

---

## Phase 7 — delivered artifacts

Clean-checkout reproduction (`rm -rf build */build .gradle && ./gradlew :common:build && ./gradlew build`):

```
/home/sanufsoii/ports/готовые порты/parcool/0.1-mc1.21.9fabric-3.4.3.3.jar
  1 208 004 bytes, 516 entries, 350 classes
  sha256 3247c7e6f8332059a2b6511d0b7cece1f02d18b57d3e8c870b5c75fb8de1d1d3
/home/sanufsoii/ports/готовые порты/parcool/0.1-mc1.21.9neoforge-3.4.3.3.jar
  1 225 053 bytes, 533 entries, 361 classes
  sha256 16983f865c3b52ea6705c7545c56f26093affb92467ab6235e082bfbbc0ac87b
```

**Both of those were superseded by the `refCnt: 0` network fix further down, and the pair now
published is:**

```
/home/sanufsoii/ports/готовые порты/parcool/0.1-mc1.21.9fabric-3.4.3.3.jar
  1 208 041 bytes, 516 entries, 350 classes
  sha256 9a4d9948321e0cf33195c49f93b383fef45eae011563a2ec3cafbfc624481f77
/home/sanufsoii/ports/готовые порты/parcool/0.1-mc1.21.9neoforge-3.4.3.3.jar
  1 225 035 bytes, 533 entries, 360 classes
  sha256 398fc4bd829842073050dcb825c1d9f91c120fd3c18e1c2193c421fca4803fc1
```

The artifacts are therefore **not** byte-for-byte identical to the pre-fix pair, but the shape is:
entry counts and class counts are identical to the pre-fix pair (516/350 and 533/360 — note the
pre-fix note above said 361 classes for the neoforge jar, which is a miscount, the jar has always
had 360), and the byte deltas are +37 and −18. The only changed content is the body of the receiver
lambda in `FabricParCoolNetwork#register` and `NeoForgeParCoolNetwork#register`.

Post-build checks run against the shipped jars (not the build tree):

* Fabric jar: every mixin target *as remapped into the bytecode* resolves in the 1.21.9 intermediary
  jar (36 targets, 350 classes); every access-widener entry resolves with its declared descriptor
  (9 entries); `accessWidener v2 intermediary`; no refmap; no mojmap reference left in any class.
* NeoForge jar: mojmap-named (verified by finding `net/minecraft/world/level/block/entity/BlockEntityType`
  in the class constant pools); carries `META-INF/neoforge.mods.toml`, `META-INF/accesstransformer.cfg`,
  `parcool.accesswidener`, `parcool-common.mixins.json`, `LICENSE` and the `ServiceLoader` file.
* Both: all assets and `data/parcool/**` present; `pack.mcmeta` decoded as COMPATIBLE for both the
  client-resource and the server-data pack type.

## The decode ran on a released buffer — `refCnt: 0`

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

## Phase 8 — the fabric-api swap, and why the first launch after it proved nothing

### Architectury 18.0.8 could not boot 1.21.9 at all

Two independent constraints have to hold, and phase 1 only recorded the first:

1. `architectury-fabric`'s own `fabric.mod.json` says `"minecraft": "~1.21.7"`. Read naively that
   looks like a refusal, but the 18.x line actually ships `~1.21.7-`, and the trailing dash makes it
   `[1.21.7, 1.22.0)` — which admits 1.21.9. 19.0.1 is the real refusal: `~1.21.11`.
2. Architectury declares `fabric-api` as a **hard, non-optional runtime dependency**, and Gradle
   resolves version conflicts with "highest wins". fabric-api is MC-pinned, so the fabric-api that
   Architectury drags in has to *lose* against this port's `fabric_api_version=0.134.1+1.21.9` — or
   Fabric Loader aborts the boot before the window exists.

Read out of the published `.module`/`.pom` metadata on maven.architectury.dev:

| architectury | fabric-api it drags in | that fabric-api wants | vs the port's `0.134.1+1.21.9` | result |
|---|---|---|---|---|
| 18.0.8 | 0.136.0+1.21.10 | MC `[1.21.10, 1.21.11)` | higher → architectury wins | **boot aborts** |
| 18.0.5, 18.0.4 | 0.133.14+1.21.9 | MC `[1.21.9, 1.21.10)` | lower → the port wins | ok |
| 18.0.3, 18.0.2 | 0.133.14+1.21.9 | MC `[1.21.9, 1.21.10)` | lower → the port wins | ok |
| 17.0.8 | 0.128.1+1.21.7 | MC `[1.21.7, 1.21.8)` | lower → the port wins | ok |

The `0.136.0+1.21.10` failure is captured twice in this port's own run logs, so it is not a theory —
`fabric/run/logs/2026-09-30-1.log.gz` (09:06) and `2026-09-30-2.log.gz` (09:11) are both aborted boots:

```
[FabricLoader/Resolution] Immediate reason: [HARD_DEP architectury 18.0.8 {depends fabric-api @ [>=0.127.0]},
  HARD_DEP_INCOMPATIBLE_PRESELECTED fabric-api 0.136.0+1.21.10 {depends minecraft @ [>=1.21.10- <1.21.11-)},
  ROOT_FORCELOAD_SINGLE architectury 18.0.8]
[FabricLoader/ERROR] Incompatible mods found!
```

`architectury_api_version` is therefore `18.0.5`, not `18.0.8`. The 18.x line on
maven.architectury.dev is 18.0.2, 18.0.3, 18.0.4, 18.0.5 and 18.0.8 — 18.0.6 and 18.0.7 were never
published — so 18.0.5 is the newest build satisfying both constraints. The chain is confirmed by the
log itself: the run that got past resolution reports `- fabric-api 0.134.1+1.21.9` in its mod list,
i.e. the port's declaration won, exactly as "highest wins" predicts.

API compatibility was checked rather than assumed. `javap` over the `dev.architectury` classes
ParCool references gives byte-identical signatures in architectury{,-fabric,neoforge} 18.0.5 and
18.0.8, with one difference in the port's favour: 18.0.5 still declares
`ClientGuiEvent.DEBUG_TEXT_LEFT/DEBUG_TEXT_RIGHT`, which 18.0.8 removed. ParCool uses neither
(`grep -r DebugText common/src fabric/src neoforge/src` is empty), so the downgrade is a strict
superset for this port. architectury-neoforge 18.0.5 declares the same
`minecraft [1.21.4,)` / `neoforge [21.0.110-beta,)` as 18.0.8, so it accepts 21.9.16-beta.

### The Architectury mixin warnings are harmless, and here is why

```
[FabricLoader/Mixin] Compatibility level JAVA_16 specified by architectury-common.mixins.json is
  higher than the maximum level supported by this version of mixin (JAVA_13)
[FabricLoader/Mixin] architectury-common.mixins.json:inject.MixinBlock from mod architectury:
  Class version 65 required is higher than the class version supported by the current version of
  Mixin (JAVA_16 supports class version 60)
```

This is noise, and the log proves it rather than arguing it:

* Both messages are about the **`compatibilityLevel` field of the mixin config**, i.e. the bytecode
  level Mixin is *allowed to emit into Minecraft's classes*. SpongePowered Mixin 0.8.7 caps that at
  `JAVA_13` and Architectury asks for `JAVA_16`. Mixin downgrades its own output level and warns.
  It does not disable the injector.
* Class version 65 is Java 21, which is what 1.21.9 itself runs on. The "class version supported by
  the current version of Mixin (JAVA_16 supports class version 60)" half of the message is Mixin
  comparing against the *downgraded* `JAVA_16` level it just adopted, so it is downstream of the same
  cosmetic clamp. Note that the identical warning is emitted for `fabric-block-view-api-v2`, i.e. for
  a mod that is not Architectury at all — a warning that fires for unrelated mods cannot be an
  Architectury-specific breakage.
* The decisive evidence is that the mixins **were applied**: the log contains
  `Mixing inject.MixinBlock from architectury-common.mixins.json into net.minecraft.world.level.block.Block`
  (the very mixin named in the second warning), plus `MixinEntity`, `MixinPlayer`, `MixinServerPlayer`,
  `MixinLivingEntity`, `MixinServerLevel`, `BiomeAccessor`, `MixinFarmBlock`, `MixinItemEntity` and
  dozens more, and on the client side `client.MixinClientPacketListener from architectury.mixins.json
  into net.minecraft.client.multiplayer.ClientPacketListener`. A mixin that Mixin refused to apply
  would never produce a `Mixing …` line.

So Architectury's own mixins are live on 1.21.9 in this port. The earlier "open risk" listed in
*Not verified* is now closed for the mixin-application half: they apply, and no injector failed
(`defaultRequire: 1` would have logged a critical injection failure, and there is none). What
remains unverified is still the *behaviour* behind them.

### The first launch after the downgrade is not a pass, and the log says so

`fabric/run/logs/latest.log` covers 09:24:40–09:30:14 and contains no error of any kind relating to
ParCool. It is tempting to call that a clean bill of health. It is not one, and the log itself is why.
What is actually in it:

* all eleven receivers registered — `Registering C2S receiver with id parcool:payload.custom_stamina.c2s`,
  `parcool:payload.client_info.c2s`, `parcool:payload.stamina.c2s`, `parcool:payload.action_state.c2s`,
  and the seven S2C ids, lines 516–526;
* `fabric-api 0.134.1+1.21.9` loaded, so the downgrade did its job;
* the run **did** get into a world — the integrated server started at 09:28:32, `Player172` logged in
  with entity id 25 at 09:28:36 and joined, `Loaded 12 advancements` at 09:30:10, several gamemode
  commands, a screenshot. It stayed in the world for ~95 s;
* ParCool logged `Limitation of 31a9aee6-8c2b-389d-b14e-b75d71479611 was loaded`;
* and yet **not one** `IllegalReferenceCountException`, and **not one** "the server limitation snapshot
  has been missing" warning. The watchdog never fired, which means ParCool never became active,
  which means the limitation snapshot S2C never arrived.

So the session neither refutes nor confirms the network bug: it shows the release-after-return path
was simply never exercised, because no ParCool payload finished arriving in either direction. The
world had no limitations configured, so the server never had a reason to send a snapshot. Note also
that **ParCool logs nothing at all on its send or receive path** — "there are no ParCool network
lines in the log" is not evidence that packets did not move, it is evidence that the mod has no
logging there, and its absence cannot be read in either direction.

What could not be settled from the log, and was not chased further because this port must not launch
the game: whether the client's C2S `client_info` — sent unconditionally from
`PlayerJoinHandler#onClientPlayerJoin`, and Architectury's `MixinClientPacketListener` that fires
that event *was* applied (see above) — was ever accepted by the integrated server. If it was, the
released-buffer path would have thrown; since nothing threw, the most consistent reading is that no
ParCool payload completed the journey, not that the code is sound. That is a hypothesis, and it is
labelled as one.

The lesson, written down so the next port does not repeat the mistake: **a clean `latest.log` is not
a pass.** A boot proves the mod loads. Only a session in which a ParCool packet is actually delivered
proves the network works.

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
