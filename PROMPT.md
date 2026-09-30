# PROMPT.md — ParCool port to Minecraft 1.21.8

**Read this whole file before touching anything. Then work through it phase by phase and commit as
you go. Do not ask for permission between phases — the task is fully specified here.**

## 0. What this task is

Port the Minecraft mod **ParCool! 3.4.3.3** (by alRex_U, LGPL-3.0) to **Minecraft 1.21.8** on
**Architectury API**, so that one codebase ships both a Fabric and a NeoForge artifact.

**This is a port, not a new mod.** Every line of behaviour you produce must be traceable to the
reference port or to upstream ParCool. Do not add features, do not "improve" gameplay, do not add
configuration keys that do not exist upstream, do not add chat messages or diagnostics the user did
not ask for. If you find an upstream bug, write it down in `NOTES.md` — do not silently redesign.

## 1. References — READ-ONLY

| Path | What it is |
|---|---|
| `/home/sanufsoii/Developer/майнкрафт модинг/parcool/parcool-Architectury-API-1.21.11` | **Your base.** The finished 1.21.11 port. Copy its structure. |
| `/home/sanufsoii/Developer/майнкрафт модинг/parcool/parcool-Architectury-API-1.21.1` | A second, *finished* port of the same mod to an older version. Use it as the worked example of "what a completed multiloader port looks like", and as a source of fixes. |
| `/home/sanufsoii/ports/готовые порты/parcool/` | Where finished jars are published. |

**Treat both reference projects as read-only.** If you believe one of them needs a change, write it
in `NOTES.md` and move on. Your work happens in this folder only.

Read `README.md` and `BUILDING.md` in the 1.21.11 project first. They explain the module layout, the
packaging rationale, and the loader-independence check.

## 2. Ground rules

1. **Base = the 1.21.11 tree.** Copy it here, then change what the target version forces you to
   change. Do not start from a fresh Gradle skeleton; you would lose every fix that the 1.21.11 port
   already contains.
2. **Before you rewrite anything, read the corresponding file in the 1.21.1 port.** A large share of
   the two trees is identical logic. The 1.21.1 port also contains fixes that the 1.21.11 port
   *dropped* (e.g. `Limitations` filename validation). Diff first, port second.
3. **Keep the mod identity:** id `parcool`, name `ParCool!`, version `3.4.3.3`, author `alRex_U`,
   license LGPL-3.0, and the upstream links (`alRex-U/ParCool`, CurseForge). The loader wrappers
   (`ParCool Architectury API port`) may stay credited as they are in 1.21.11.
4. **`common` stays loader-agnostic.** No `net.fabricmc.*`, no `net.neoforged.*` imports under
   `common/src/main/java`. `./gradlew :common:checkCommonLoaderIndependence` must pass.
5. **No source-set split.** `common` stays a single tree holding `Action`, `KeyRecorder` and the
   animation classes, exactly like the upstream NeoForge mod and like both reference ports.
6. **Comments are the deliverable, not overhead.** Every workaround you introduce gets a comment
   saying what breaks without it. Both reference ports do this; match that density. A future reader
   cannot tell from a diff why a strange line exists.
7. **Verify, do not assume.** Every version number and every API name in this file is a *hypothesis*.
   The version numbers are deliberately left as `TODO` — resolve each one from the metadata endpoint
   listed in phase 1 and write it into `gradle.properties` yourself.

## 3. Phase 0 — orient

```bash
cd "/home/sanufsoii/Developer/майнкрафт модинг/parcool/parcool-Architectury-API-1.21.11"
git log --oneline          # the 1.21.11 port's own history
cat README.md BUILDING.md
```

Then create `NOTES.md` in this folder and keep it up to date as you work. It is where findings,
dead ends, and upstream bugs go. Commit it.

## 4. Phase 1 — pin the toolchain

Copy the 1.21.11 project into this folder, then resolve the toolchain. **Do not guess versions.**
Look them up:

```bash
# Minecraft -> Fabric Loader / Fabric API mapping
curl -s https://meta.fabricmc.net/v2/versions/game | head -c 400

# Architectury API: which line targets your MC version
curl -s https://maven.architectury.dev/dev/architectury/architectury-fabric/maven-metadata.xml

# NeoForge
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml

# Architectury Loom + ModDevGradle plugin versions
curl -s https://maven.architectury.dev/dev/architectury/loom/maven-metadata.xml
curl -s https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/net.neoforged.moddev.gradle.plugin/maven-metadata.xml
```

Known-good reference values, for calibration only:

| | 1.21.1 port | 1.21.11 port |
|---|---|---|
| `minecraft_version` | `1.21.1` | `1.21.11` |
| `neo_version` | `21.1.217` | `21.11.45` |
| `loader_version` (Fabric) | `0.16.5` | `0.19.5` |
| `fabric_api_version` | `0.116.15+1.21.1` | `0.141.6+1.21.11` |
| `architectury_api_version` | `13.0.11` | `19.0.1` |
| `dev.architectury.loom` | `1.7.435` | `1.17.493` |
| `net.neoforged.moddev` | `1.0.9` | `2.0.147` |
| Gradle wrapper | `8.10.2` | `9.4.1` |
| Java toolchain | 21 | 21 |

Write the resolved values into `gradle.properties` and `settings.gradle`, and record in `NOTES.md`
where each came from. If Loom 1.7 warns that it is unsupported, that is expected for old MC targets;
a newer Loom is usually fine and preferred if it still configures the older Minecraft.

Then confirm the project configures before writing any Java:

```bash
./gradlew :common:build
```

## 5. Phase 2 — get `:common` compiling

Port `common/src/main/java` to the target API. Work in dependency order so you are not chasing
compiler errors through 250 files at once:

```
ParCool.java → platform/ → common/data/ → common/action/ (+ impl/) →
common/block/ → common/entity/ → common/item/ → common/network/ →
common/zipline/ → common/potion/ → common/stamina/ → common/info/ →
common/handlers/ → common/event/ → config/ → client/
```

Rules that save a lot of time:
- Compile after each group, not at the end: `./gradlew :common:compileJava --offline`.
- **Never work around a missing vanilla member with reflection, string lookups, or a no-op.** Widen
  it. `common/src/main/resources/parcool.accesswidener` exists for exactly this, and
  `neoforge/src/main/resources/META-INF/accesstransformer.cfg` is the NeoForge equivalent. Both
  reference ports have members that need widening on one loader and not the other; keep that
  per-loader split, and comment which side needs what.
- When vanilla renamed a method, use the new name. Do **not** keep a deprecated alias.
- The 1.21.1 port is written against the *older* API; its code is the better reference for the
  target version whenever the target is closer to 1.21.1 than to 1.21.11. Decide per file.

## 6. Phase 3 — the version deltas that will actually bite

These are the seams between 1.21.1 and 1.21.11. For each one, **find out for yourself whether your
target version is on the old or the new side** (the decompiled sources or the mappings in the Loom
cache are the authority), then port accordingly. Copy the 1.21.11 side unless you find the target is
still old-side, in which case copy the 1.21.1 side.

> **Corrected for 1.21.8** (see NOTES.md §"PROMPT.md corrections"). 1.21.8 was released 2025-07-17 as
> a pure bugfix release on top of 1.21.7, so it sits on the **1.21.1 side of most of these seams** and
> on a **three-way hybrid** on the three render rows. The left column below is the 1.21.1 side and is
> what this port uses; the right column is only what 1.21.11 needs.

| Area | 1.21.1 (old side) — **what 1.21.8 uses** | 1.21.11 (new side) |
|---|---|---|
| `ResourceLocation` vs `Identifier` | `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| Attribute registration on NeoForge | direct `Registry.registerForHolder` from the common entry point | `BuiltInRegistries` is frozen before mod constructors ⇒ `:neoforge`'s `NeoForgeAttributes` (NeoForge `DeferredRegister` on the mod event bus) and `Attributes` *resolves* the holder instead of writing it; `ParCoolNeoForge` takes a `ModContainer` to get that bus; `Attributes.registerAll()` is called only from the Fabric entry point. **1.21.8 takes the NEW side here** (NeoForge ≥ 21.5 freezes the built-in registries before the mod constructors) |
| Attribute holder lookup | `Registry#get` returns the value, not an `Optional` | `Registry#get` returns an `Optional`. **1.21.8 takes the NEW side** |
| Entity / BlockEntity save | `CompoundTag` (`readAdditionalSaveData` / `addAdditionalSaveData`) | `ValueInput` / `ValueOutput`. **1.21.8 takes the NEW side** |
| `BlockEntityType` construction | `BlockEntityType.Builder` | `Builder` deleted; the port reaches the private constructor through `mixin.common.BlockEntityTypeInvoker` and `ParCoolPlatform#registerBlockEntityType`. **Neither applies to 1.21.8**: there is no `Builder` *and* no static `register`, only the package-private 2-arg constructor plus the package-private nested `BlockEntitySupplier`. Widen both and call the constructor from a plain Architectury `DeferredRegister`; the platform seam and the invoker mixin are unnecessary |
| Render types | `RenderStateShard` in `RenderType` | `RenderSetup` around a `RenderPipeline`; `RenderPipelines#PIPELINES_BY_LOCATION`. **1.21.8 is a hybrid**: `RenderStateShard` + `CompositeState` + `RenderPipeline`, but `RenderStateShard#CULL` / `#NO_CULL` / `RENDERTYPE_LEASH_SHADER` and the format-carrying `RenderType#create` overload are gone, so the zipline rope has to build its own pipeline from `RenderPipelines#MATRICES_FOG_SNIPPET` and put it into `PIPELINES_BY_LOCATION` |
| Entity rendering | `EntityRenderer#render(...)` draws directly | `extractRenderState` / `submit(...)` with a `SubmitNodeCollector`; renderers are stateless; `AvatarRenderer` + `IAvatarRenderStateEntity`. **1.21.8 is a hybrid**: `EntityRenderer<T, S extends EntityRenderState>` with `extractRenderState` + `render(S, PoseStack, MultiBufferSource, int)`; no `SubmitNodeCollector`, no `CameraRenderState`, no `AvatarRenderer` (it is `PlayerRenderer` + `PlayerRenderState`) |
| Key mappings | category is a `String`; `KeyMapping.MAP` is `Map<Key, KeyMapping>` — **one mapping per physical key** | `KeyMapping.Category` record; `KeyMapping.MAP` is `Map<Key, List<KeyMapping>>`. **1.21.8 takes the old side** |
| The one-mapping-per-key conflict | 1.21.1 needs `KeyBindings#restoreVanillaBindings()` (reflection into `KeyMapping.MAP`/`ALL`) because ParCool binds 16 keys that vanilla also owns, and the last registration evicts vanilla's mapping | the table allows several mappings per key, so the repair is dead code and 1.21.11 deleted it. **1.21.8 needs the repair** — this is the single most player-visible difference on Fabric |
| Recipe ingredients | string form, e.g. `"minecraft:chain"`, results as `{"id": ...}` | object form, e.g. `{"item": "minecraft:iron_chain"}`. **1.21.8 takes the 1.21.1 side** (1.21.9 is where the object form arrives) |
| Recipe `category` | **required** by vanilla's `ShapedRecipe$Serializer` / `ShapelessRecipe$Serializer` codec (`Codec.fieldOf("category")`, not `optionalFieldOf`) on 1.21.1…1.21.8 | same. Every `data/<ns>/recipe/*.json` needs it or the datapack drops the recipe with "Missing field category" and **every ParCool item silently becomes uncraftable while the build stays green** |
| `pack.mcmeta` | `pack_format: 34` | `pack_format: 81` plus `min_format`/`max_format`/`supported_formats`. **1.21.8's `PackMetadataSection` has only `description`, `pack_format` and `supported_formats` — `min_format`/`max_format` do not exist and are silently dropped.** The real numbers for 1.21.8 are `RESOURCE_PACK_FORMAT = 64` and `DATA_PACK_FORMAT = 81` (read them out of the jar, do not copy 34 or 81) |
| `Entity#isInWaterOrBubble` | present | removed; the 1.21.11 port reimplements it in `utilities/EntityUtil`. **1.21.8 takes the new side** (removed) |
| `Player#canInteractWithEntity` | present | removed. **1.21.8 takes the old side** (still present) |
| `Entity#hurt` | single overridable method | `public final void hurt(...)` + `hurtOrSimulate` + `abstract hurtServer`. **1.21.8 takes the new side**; damage redirects must target the `void hurt` call site |
| `jumpFromGround` | on `Player` | moved to `LivingEntity`; the port's hooks moved to a new `mixin.common.LivingEntityJumpMixin`. **1.21.8 takes the new side** |
| `Player#causeExtraKnockback` | n/a (the `setSprinting(false)` call is inline in `Player#attack`) | the whole knockback block was extracted. **1.21.8 takes the old side**: `causeExtraKnockback` does not exist, so a `@WrapWithCondition` has to target `attack` or Loom will not remap it |
| `Item` description id | `BlockItem#getDescriptionId` delegates to the block | stored field set at construction ⇒ item models moved to `assets/parcool/items/*.json`. **1.21.8 takes the new side** *and* therefore needs `Item.Properties#useBlockDescriptionPrefix()` on the two hook items, or every hook becomes `item.parcool.*` and the `block.parcool.*` keys in the eleven language files go unused. The item *definitions* live in `assets/parcool/items/*.json`; the *model files* they name still live in `assets/parcool/models/item/*.json` and are still required on 1.21.8. **Do not delete `models/item/**`.** (An earlier version of this row called it dead; that was wrong and cost three items their inventory icons — see NOTES.md) |
| `Item.Properties#setId` | not needed | mandatory (`Objects.requireNonNull(this.id, "Item id not set")`). **1.21.8 takes the new side**, and Architectury's `DeferredRegister` does *not* set it — without a manual `setId(ResourceKey.create(Registries.ITEM, …))` the mod does not initialise on **either** loader. `BlockBehaviour.Properties#setId` is the same story, and `noCollission()` (vanilla's typo) replaces `noCollision()` |
| `KeyboardInput#tick` | no arguments | same. The `@Inject` handler must be `private void …(CallbackInfo ci)` with `method = "tick()V"`; a handler that takes `(boolean, float, CallbackInfo)` compiles and is a **hard boot failure** under `defaultRequire: 1` |
| `InteractionResult` | enum | interface with `SUCCESS` / `SUCCESS_SERVER` constants. **1.21.8 takes the new side** |
| `Input` | plain class | record of `forward/backward/left/right/jump/shift/sprint`; `ClientInput.keyPresses` is the `Input`. **1.21.8 takes the new side** |
| `ItemTintSources` | absent (item colours via `ItemColor`) | codec-keyed `net.minecraft.client.color.item.ItemTintSources`. **1.21.8 takes the new side**; `net.minecraft.client.color.item.ItemColor` no longer exists and Architectury has no colour handler registry, so the codec registry is the only route |
| Translation keys | `key.categories.parcool` | `key.category.parcool`. **1.21.8 takes the old side** |
| NeoForge mapping naming | **mojmap**, despite the `client-…-srg.jar` filename | mojmap as well (verified against a shipped NeoForge mod) — re-verify for your NeoForge version, do not assume |

**Do not skip the one-mapping-per-key row.** It is Fabric-only and it is the single most
player-visible difference between the two reference ports: on a loader where `KeyMapping.MAP` holds
one mapping per physical key, ParCool silently steals right-click / Space / Left-Ctrl from vanilla
unless the table is repaired. If your target version has the single-mapping table, port
`restoreVanillaBindings` (from the 1.21.1 tree) and the `KeyRecorder#onClientTick` call that drives
it, and comment why. If it has the multi-mapping table, do not port the reflection at all.

Also check the two fixes below, which are the same on every version and easy to lose when copying:

- `KeyBindings#isDown` / `isMetaKeyDown` must reject an unbound keysym before calling `glfwGetKey`.
  An unbound binding is `GLFW_KEY_UNKNOWN` = `-1`, and GLFW answers with `GLFW_INVALID_ENUM`
  (`0x00010003`, printed as `65539: Invalid key -1` in the log) once per poll per unbound key — tens
  of spam lines per second. This bit both reference ports before it was fixed.
- `ZiplineRopeEntity#addAdditionalSaveData` must write six distinct NBT keys. Writing
  `"Tile1_X"` three times silently drops the Y and Z of both rope ends, so every zipline collapses
  onto a 1×0×1 line after a chunk reload.

## 7. Phase 4 — mixins

Copy `parcool-common.mixins.json` from 1.21.11 and fix it up: keep only the mixins that exist in this
tree, and use the 1.21.1 mixin set as the fallback shape where a 1.21.11 mixin exists purely because
1.21.11 needed it. `client.ClientPacketListenerMixin` exists in the 1.21.1 port and not in 1.21.11;
`client.AvatarRenderStateEntityMixin`, `client.AvatarRenderStateExtractorMixin`,
`client.ItemTintSourcesAccessor`, `common.BlockEntityTypeInvoker` and `common.LivingEntityJumpMixin`
exist in 1.21.11 and not in 1.21.1. Classify each one against the target and keep the side that
applies.

Every mixin target must be re-derived for the target version — a `@Inject(method = "...")` that
silently stops matching is a runtime no-op, and with `defaultRequire: 1` a wrong one is a hard boot
failure instead. Verify by actually booting, in phase 6.

## 8. Phase 5 — build and packaging

Copy the 1.21.11 `build.gradle` files. The two loaders are **not** built the same way and the reasons
are load-bearing:

- **Fabric**: the distributable comes from `:common:remapJar` (Loom's own publish step, which applies
  the access widener, converts named → intermediary and — with Loom's default
  `mixinRemapType = static` — rewrites the mixin targets straight into the bytecode). There is
  deliberately **no refmap**, and the Fabric jar must carry the access widener in `v2 intermediary`
  (`v2 named` makes Fabric Loader 0.19.x abort the boot before the window exists). The order of the
  `from { }` clauses in `distJar` matters: first contributor wins because `duplicatesStrategy` is
  `EXCLUDE`.
- **NeoForge**: mojmap-named bytecode, because the production runtime loads mojmap. architectury-plugin's
  `neoForge()` transform is **not** used — it needs a Loom-based NeoForge setup that cannot merge the
  Mojang and NeoForge mappings, so `:neoforge` assembles the distributable explicitly and consumes
  `:common`'s `transformProductionNeoForge` artifact as a file dependency.

Keep these. They are the result of a lot of pain, and the comments in the 1.21.11 `build.gradle`
explain each one. If your target version needs a different packaging, change it deliberately and
record why in `NOTES.md`.

**Loom cache trap.** Loom keeps a per-consumer remapped copy of `:common` under
`.gradle/loom-cache/remapped_mods`. If a class you just added is missing at runtime, or a dev run
silently behaves like the old code, that cache is stale:

```bash
rm -rf .gradle/loom-cache common/build/devlibs common/build/loom-cache fabric/build/loom-cache
```

Do this whenever you add a class to `:common`. It will otherwise make you debug a build that is not
the one you are looking at. **Never run `./gradlew --stop`** on this machine — three ports build in
parallel and the Gradle daemons are shared.

## 9. Phase 6 — actually run both loaders

A port that only compiles is not a port.

> **CANCELLED for this tree.** The brief for this port forbids launching Minecraft in any form (dev
> run, dedicated server, headless client). Acceptance is `./gradlew build` plus the contents of the two
> artifacts, and the whole runtime checklist below is *open*. BUILDING.md and NOTES.md say so in the
> same words; do not tick anything here.

```bash
./gradlew build
./gradlew :fabric:runClient
./gradlew :neoforge:runclient
```

Before you start, decide how you will get into a world without a mouse, and put it in the run config
if it is not already there — a `--quickPlaySingleplayer <world>` / `--quickPlayMultiplayer <host:port>`
property is the cheapest option and the 1.21.11 project already has one. Boot into a world, then
check all of this:

- [ ] The game starts, on **both** loaders, with the mod listed and no failed mod state.
- [ ] Enter a world. The ParCool attributes resolve on the first `Player#createAttributes` — that is
      the step that dies with `Registry is already frozen` if the NeoForge attribute split is wrong.
- [ ] `grep "GL ERROR" <log>` is empty. Also `grep "Invalid key"` — the GLFW keysym guard.
- [ ] Every key binding listed in `assets/parcool/lang/en_us.json` under `key.parcool.*` is
      rebindable in Options → Controls, and pressing it actually drives its action.
- [ ] A vanilla key that ParCool also binds still works (right-click places a block, Space jumps,
      Ctrl sprints) **on Fabric**. If it does not, the `KeyMapping.MAP` repair is missing.
- [ ] Do one action of each family: wall run, wall jump, slide, roll, dodge, vault, hide-in-block,
      zipline ride, stamina HUD, the settings screen.
- [ ] Two clients on one server see each other's animations.
- [ ] `:common:checkCommonLoaderIndependence` passes.

Then install the built jar into a real Prism instance and boot it there too. A jar that works in the
dev environment and dies on a production client is a common failure mode: the Fabric access-widener
namespace and the NeoForge mapping naming are both exactly this.

## 10. Phase 7 — deliver

Name the artifacts after the existing convention, and publish them alongside the others:

```
0.1-mc1.21.8fabric-3.4.3.3.jar
0.1-mc1.21.8neoforge-3.4.3.3.jar
```

Copy them to `/home/sanufsoii/ports/готовые порты/parcool/`.

Then write the two docs for this version, based on the 1.21.11 ones and with the version's real
numbers in them: `README.md` (what this port is, requirements, install) and `BUILDING.md` (toolchain
table, module layout, run tasks, packaging rationale).

**Do not touch the `26.x` folders.** They are out of scope for this task.

## 11. Git

This folder is a fresh project and has no repository yet. Create one on your first run and commit as
you go — after each phase, not at the end. Without history the next person repeats the mistake that
made this port's 1.21.1 tree unrecoverable in the first place.

```bash
git init
git add -A
git commit -m "Port ParCool! to the Architectury API (Fabric + NeoForge) for MC 1.21.8"
```

Suggested commit boundaries, one commit each:

- the imported 1.21.11 tree, before you change anything
- the resolved toolchain (`gradle.properties` / `settings.gradle` / wrapper)
- `:common` compiling, split by dependency group
- the render layer retargeted
- the entity/attribute registration retargeted
- the mixin set rewritten
- resources and recipes
- packaging
- `README.md` + `BUILDING.md` + `NOTES.md`

Message style, matching the 1.21.11 history: imperative, one line, no emoji, no issue numbers you
did not create.

## 12. Definition of done

- [ ] `./gradlew :common:build && ./gradlew build` succeeds from a clean checkout (delete `build/`,
      `.gradle/`, retry). **One `./gradlew build` on a cold checkout does not work** — Architectury
      Loom resolves the `:common` jar while it *configures* `:fabric`, so `:common` has to be built
      once first. This is inherited from the 1.21.11 tree; document it, do not "fix" it.
- [ ] Both jars exist, are published, and their contents are checked (class counts, the mixin config
      against the classes in the jar, the access widener in `v2 intermediary` with no refmap in the
      Fabric jar, mojmap mixin targets in the NeoForge jar).
- [ ] Both loaders boot into a world, tested in a real Prism instance, not only in dev. — **open:
      launching the game is forbidden for this tree.**
- [ ] `checkCommonLoaderIndependence` passes.
- [ ] No leftover debug code: no `System.out`, no `printStackTrace`, no `*-probe` log lines, no
      commented-out blocks, no absolute local paths, no machine-specific paths in the build.
- [ ] No unused imports *added by this port*. The ~26 the upstream sources carry are inherited from
      both reference trees — do not sweep them here, that is an unrelated change.
- [ ] `.gitignore` covers `.gradle/`, `build/`, the run directories, `*.log`, `*.txt`, `/*.jar`,
      `.architectury-transformer/`, `**/loom-cache/`, `**/explodedCommon/`.
- [ ] `NOTES.md` records: the resolved toolchain and where each number came from, every version
      delta you had to decide, every upstream bug you found but did not fix, and anything you think
      the next port should not trust.
- [ ] Commits exist and are readable.
- [ ] Every `mixins.json` entry has a class in the jar **and** every mixin class in the jar is in the
      config; every `@Inject`/`@Redirect`/`@WrapWithCondition` target — including the handler
      descriptor — has been checked against the target jar with `javap`. A verifier that reports "0
      problems" has to have been self-tested on a deliberately broken target first: a regex like
      `\(([^)]*)\)` silently truncates on the `)` inside `Lnet/minecraft/world/phys/Vec3;)V` and skips
      every mixin that has a descriptor.

If something in this brief turns out to be wrong for your version, **fix the brief**: correct the
file, note it in `NOTES.md`, and carry on. A wrong line in a handoff document is worse than no line.
