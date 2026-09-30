# PROMPT.md — ParCool port to Minecraft 1.21.3

**Read this whole file before touching anything. Then work through it phase by phase and commit as
you go. Do not ask for permission between phases — the task is fully specified here.**

## 0. What this task is

Port the Minecraft mod **ParCool! 3.4.3.3** (by alRex_U, LGPL-3.0) to **Minecraft 1.21.3** on
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

> **Calibration note from the 1.21.3 port.** 1.21.3 sits between the two columns above:
> `architectury_api_version = 14.0.4` (the 14.x line targets 1.21.2/1.21.3; 13.x is the 1.21.1 line
> and 15.x starts at 1.21.4), `neo_version = 21.3.97`, `loader_version = 0.16.10`,
> `fabric_api_version = 0.114.1+1.21.3`, Loom `1.7.435`, moddev `1.0.24`, wrapper `8.10.2`. See
> `NOTES.md` §1 for how each was derived. The Architectury line number is the one that decides
> whether a *networking* bug you hit is a port bug or an upstream one — see the networking warning
> in §6.

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

> **Correction (written by the 1.21.3 port, see `NOTES.md` §2).** The table below has only two
> columns, and 1.21.3 is on the **new** side of six rows that are marked "1.21.1 (old side)", because
> 1.21.2 was a large internal rework that 1.21.3 sits directly on top of. The rows that are wrong for
> 1.21.3 are: `BlockEntityType` construction, `jumpFromGround`, `Entity#hurt` (implied by
> "Entity / BlockEntity save" being `CompoundTag`-shaped on the 1.21.1 side is still true, but `hurt`
> is not), `HumanoidModel`/`PlayerModel` type parameters, and `BlockBehaviour`/`Item` registry keys
> (not listed at all). The rows that are *correct* for 1.21.3: `ResourceLocation`, save/load,
> `Block#useItemOn`, `Block#updateShape`, `EntityType.Builder#build`, `Registry#get`,
> `Item#descriptionId`, recipe ingredients, `pack.mcmeta` (but see below), `KeyMapping` category and
> `KeyMapping.MAP`, `ClientInput`, `isInWaterOrBubble`, `canInteractWithEntity`, item model location.
>
> 1.21.3 is its own case in three rows, i.e. **neither** column applies:
> * *Entity rendering*: the render-state split (`EntityRenderer<T, S extends EntityRenderState>` with
>   `extractRenderState`) **is** present, but `SubmitNodeCollector` and `submit(...)` are **not** —
>   `render(S, PoseStack, MultiBufferSource, int)` still draws. The 1.21.11 mixin set
>   (`AvatarRenderStateEntityMixin`, `AvatarRenderStateExtractorMixin`) has to be rebuilt around
>   `PlayerRenderState` / `PlayerRenderer` instead of `AvatarRenderState` / `AvatarRenderer`.
> * *`pack.mcmeta`*: 1.21.3's `PackMetadataSection` has only `description`, `pack_format` and an
>   optional `supported_formats`. The `min_format` / `max_format` keys in the 1.21.11 file do not
>   exist here, and the resource / data formats are 42 and 57.
> * *Loom*: `useLegacyMixinAp` defaults to `true` on the Loom 1.7 line, so the static mixin remap has
>   to be requested explicitly — the 1.21.11 tree can rely on the Loom 1.17 default, this one cannot.

These are the seams between 1.21.1 and 1.21.11. For each one, **find out for yourself whether your
target is on the old or the new side** (the decompiled sources or the mappings in the Loom
cache are the authority), then port accordingly. Copy the 1.21.11 side unless you find the target is
still old-side, in which case copy the 1.21.1 side.

| Area | 1.21.1 (old side) | 1.21.11 (new side) |
|---|---|---|
| `ResourceLocation` vs `Identifier` | `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| Attribute registration on NeoForge | direct `Registry.registerForHolder` from the common entry point | `BuiltInRegistries` is frozen before mod constructors ⇒ `:neoforge`'s `NeoForgeAttributes` (NeoForge `DeferredRegister` on the mod event bus) and `Attributes` *resolves* the holder instead of writing it; `ParCoolNeoForge` takes a `ModContainer` to get that bus; `Attributes.registerAll()` is called only from the Fabric entry point |
| Attribute holder lookup | `Registry#get` returns the value, not an `Optional` | `Registry#get` returns an `Optional` |
| Entity / BlockEntity save | `CompoundTag` (`readAdditionalSaveData` / `addAdditionalSaveData`) | `ValueInput` / `ValueOutput` |
| `BlockEntityType` construction | `BlockEntityType.Builder` | `Builder` deleted; the port reaches the private constructor through `mixin.common.BlockEntityTypeInvoker` and `ParCoolPlatform#registerBlockEntityType` |
| Render types | `RenderStateShard` in `RenderType` | `RenderSetup` around a `RenderPipeline`; `RenderPipelines#PIPELINES_BY_LOCATION` |
| Entity rendering | `EntityRenderer#render(...)` draws directly | `extractRenderState` / `submit(...)` with a `SubmitNodeCollector`; renderers are stateless; `AvatarRenderer` + `IAvatarRenderStateEntity` |
| Key mappings | category is a `String`; `KeyMapping.MAP` is `Map<Key, KeyMapping>` — **one mapping per physical key** | `KeyMapping.Category` record; `KeyMapping.MAP` is `Map<Key, List<KeyMapping>>` |
| The one-mapping-per-key conflict | 1.21.1 needs `KeyBindings#restoreVanillaBindings()` (reflection into `KeyMapping.MAP`/`ALL`) because ParCool binds 16 keys that vanilla also owns, and the last registration evicts vanilla's mapping | the table allows several mappings per key, so the repair is dead code and 1.21.11 deleted it |
| Recipe ingredients (changed in 1.21.5) | string form, e.g. `"minecraft:chain"` | object form, e.g. `{"item": "minecraft:iron_chain"}` |
| `pack.mcmeta` | `pack_format: 34` | `pack_format: 81` plus `min_format`/`max_format`/`supported_formats` |
| `Entity#isInWaterOrBubble` | present | removed; the 1.21.11 port reimplements it in `utilities/EntityUtil` |
| `Player#canInteractWithEntity` | present | removed; the port targets `LivingEntity#getVisibilityPercent` instead |
| `jumpFromGround` | on `Player` | moved to `LivingEntity`; the port's hooks moved to a new `mixin.common.LivingEntityJumpMixin` |
| `Item` description id | `BlockItem#getDescriptionId` delegates to the block | stored field set at construction ⇒ item models moved to `assets/parcool/items/*.json` |
| Translation keys | `key.categories.parcool` | `key.category.parcool` |
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

### Networking: Architectury is not loader-agnostic here, and a client cannot tell you

> **Added by the 1.21.3 port after a live dedicated server broke it — read this before you choose a
> networking shape.** `NOTES.md` §10 has the full write-up with the bytecode and the log evidence.
> The short form, because it cost a real player-facing bug:
>
> * **Do not use `dev.architectury.networking.NetworkChannel` on Fabric.** Its `register` wraps the S2C
>   half in `if (Platform.getEnvironment() == Env.CLIENT)` (`javap -c`, offsets 90–96 of
>   `NetworkChannel.class`), so on a **dedicated server** it registers no S2C receiver, logs nothing,
>   and then `NetworkAggregator.collectPackets(sink, S2C, id, buf)` reads `null` out of the empty
>   `S2C_TYPE` map and throws `NullPointerException` on **every** server-to-client send. The server
>   still reaches `Done (…)`. Client and single player are both fine, because the integrated server
>   runs inside the client JVM.
> * **Do not call `NetworkManager.registerReceiver(S2C, …)` on a dedicated server either.**
>   `NetworkAggregator.Adaptor#registerS2C` carries `@Environment(EnvType.CLIENT)`, and Fabric Loader
>   strips such members outside a client, so it dies with `AbstractMethodError` at mod init. This is
>   true of **every** Architectury line 14–18 (checked 14.0.4, 15.0.3, 16.1.4, 17.0.6, 17.0.8, 18.0.5,
>   18.0.8) and is *not* a version skew — the method is declared with the exact interface descriptor.
> * **What to do instead**, which is also what both reference ports now do: use the id-based
>   `NetworkManager` on both loaders, one wire id per direction, and on a dedicated server register
>   the clientbound direction as a **type only**:
>
>   ```java
>   if (clientbound && Platform.getEnvironment() == Env.SERVER) {
>       NetworkManager.registerS2CPayloadType(wireId);
>       return;
>   }
>   NetworkManager.registerReceiver(clientbound ? Side.S2C : Side.C2S, wireId, (buf, context) -> { … });
>   ```
>
>   A dedicated server never *receives* a server-to-client packet, so the S2C receiver is dead weight
>   there; `registerS2CPayloadType` is Architectury's own documented answer ("For S2C types,
>   `registerReceiver` should be called on the client side, while `registerS2CPayloadType` should be
>   called on the server side") and it fills `S2C_TYPE`/`S2C_CODECS`/`S2C_TRANSFORMERS` without
>   touching the stripped member. The wire bytes do not change.
> * **The buffer contract is the other half of this, and it is not Fabric-specific:**
>   `registerReceiver` hands you a buffer and releases it the moment your receiver returns, so
>   **decode inside the receiver lambda, before `context.queue(...)`**. Decoding inside the queued
>   task throws `IllegalReferenceCountException: refCnt: 0` on the first packet that has any
>   variable-length field. See `NOTES.md` §9.
> * **Verify it on a dedicated server, from a clean Loom cache.** A dedicated server is the only
>   oracle for this class of defect: not the compiler, not `./gradlew build`, not a client, not single
>   player. Give it a `server-port` of its own — the sibling ports share this machine and all default
>   to 25565.

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
rm -rf .gradle/loom-cache/remapped_mods common/build/devlibs common/build/loom-cache fabric/build/loom-cache
```

> **`./gradlew --stop` is not available in this checkout.** Several `parcool-Architectury-API-*` ports
> build on the same machine at the same time and share the Gradle daemons, so stopping them — or
> `pkill`/`killall` — is off limits. Delete the cache directories of this project and start the build
> again; a fresh daemon is not needed. See `NOTES.md` §8.

Do this whenever you add a class to `:common`. It will otherwise make you debug a build that is not
the one you are looking at.

> Second, related trap: the `minecraft-merged-*-sources.jar` Loom writes under `.gradle/loom-cache`
> is generated **with this project's access widener already applied**. It reports post-widener access
> flags, not vanilla's. To learn whether a vanilla member is private, remove the widener entry, delete
> `.gradle/loom-cache` so the jar is regenerated, and `javap -p` the result — or just let `javac` tell
> you. See `NOTES.md` §4.

## 9. Phase 6 — actually run both loaders

> **Partly done for this port, and the order matters.** No client was ever launched here (the
> orchestrator holds the single GPU), but a **dedicated Fabric server was**: `:fabric:runServer`
> reached `Done (0.944s)` from a clean Loom cache and is what exposed the S2C registration defect in
> §6. `NOTES.md` §10. That is the whole argument for running the server even when you cannot run the
> client — a separate server process is the only thing that finds the loader-specific failures, and
> it costs no GPU. The original acceptance below (`./gradlew build` green plus artifact inspection)
> is still a valid floor, but on this port it was not sufficient: the build was green on a jar whose
> entire server-to-client path threw `NullPointerException`.

> **Not done for this port.** The 1.21.3 port was explicitly told not to launch the client, so the
> client-side acceptance used instead is: `./gradlew build` succeeds from a clean tree and both
> artifacts are inspected
> (`accessWidener` namespace, refmap present or absent, no mojmap strings in the Fabric jar, no
> intermediary strings in the NeoForge jar, mod metadata version strings). `NOTES.md` §6 lists what
> that leaves unverified, in order of risk. Everything below is therefore still worth doing, but by
> someone who is allowed to start the game.

A port that only compiles is not a port.

```bash
./gradlew build
./gradlew :fabric:runClient
./gradlew :neoforge:runclient
```

**And run a dedicated server on each loader, even if you can only do that.** Delete the Loom cache
first (`.gradle/loom-cache`, `*/build/loom-cache`, `common/build/devlibs`,
`neoforge/build/explodedCommon`) or you are measuring yesterday's jar, give it its own
`server-port` so it does not fight the sibling ports for 25565, `timeout 300`, and then:

```bash
grep -E 'Starting Minecraft server on|Done \(|AbstractMethodError|NullPointerException' <log>
grep -c 'Registering S2C receiver' <log>   # 0 on a dedicated Fabric server is CORRECT …
grep -c 'Registering C2S receiver' <log>   # … and the clientbound types must be visible some other way
```

`Done (…)` is necessary and nowhere near sufficient: on this port a fully broken server-to-client
path sat behind a green `Done`. Never run `./gradlew --stop`, `pkill` or `killall` on this machine —
the Gradle daemons are shared with the ports running in parallel.

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
0.1-mc1.21.3fabric-3.4.3.3.jar
0.1-mc1.21.3neoforge-3.4.3.3.jar
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
git commit -m "Port ParCool! to the Architectury API (Fabric + NeoForge) for MC 1.21.3"
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

- [ ] `./gradlew build` succeeds from a clean checkout (delete `build/`, `.gradle/`, retry).
- [ ] Both loaders boot into a world, tested in a real Prism instance, not only in dev.
- [ ] A **dedicated** server boots on each loader from a deleted Loom cache, and the client actually
      joins it and gets the limitation snapshot. This is the only check that finds the
      loader-specific Architectury networking failures (`NOTES.md` §10) — a client, single player and
      a green build all pass on a jar that cannot send a single server-to-client packet.
- [ ] `checkCommonLoaderIndependence` passes.
- [ ] No leftover debug code: no `System.out`, no `printStackTrace`, no `*-probe` log lines, no
      commented-out blocks, no absolute local paths, no machine-specific paths in the build.
- [ ] No unused imports.
- [ ] `.gitignore` covers `.gradle/`, `build/`, the run directories, `*.log`, `*.txt`, `/*.jar`,
      `.architectury-transformer/`, `**/loom-cache/`, `**/explodedCommon/`.
- [ ] `NOTES.md` records: the resolved toolchain and where each number came from, every version
      delta you had to decide, every upstream bug you found but did not fix, and anything you think
      the next port should not trust.
- [ ] Commits exist and are readable.

If something in this brief turns out to be wrong for your version, **fix the brief**: correct the
file, note it in `NOTES.md`, and carry on. A wrong line in a handoff document is worse than no line.
