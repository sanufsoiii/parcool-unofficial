# NOTES — ParCool Architectury port to Minecraft 1.21.10

Living document. Every resolved version number, every version-delta decision, every bug found (fixed
or not) and every dead end goes here. Written while the port is built; kept afterwards.

The authoritative brief is [PROMPT.md](PROMPT.md). Where the brief is wrong for 1.21.10 it is
corrected here *and* the fix is written back into `PROMPT.md`.

---

## 0. Orientation (phase 0)

Read-only references:

| Tree | Role |
|---|---|
| `parcool-Architectury-API-1.21.11` | base — the finished neighbouring port, copied verbatim as the starting point |
| `parcool-Architectury-API-1.21.1` | worked example of a completed multiloader port + a source of fixes the 1.21.11 tree dropped |

The 1.21.11 tree's own history is three commits: the initial port, then two fix rounds
("Fix Fabric packaging, move-tick recursion and animator tick rate",
"Move the move-recursion guard out of the mixin into a plain mod class").

Import was a `rsync` of the 1.21.11 tree minus `.git/`, `.gradle/`, `build/`, `run*/`, `*.jar`,
`*.log`, `*.txt`, `.architectury-transformer/`, plus `gradle/wrapper/gradle-wrapper.jar` copied by
hand (the `*.jar` exclude would otherwise have eaten it).

---

## 1. Resolved toolchain (phase 1)

Nothing is guessed. Each value below was read from the endpoint named in the row.

| Setting | Value | Where it came from |
|---|---|---|
| `minecraft_version` | `1.21.10` | target of the port; `https://launchermeta.mojang.com/mc/game/version_manifest_v2.json` lists `1.21.10` as a `release`, 2025-10-07 |
| `neo_version` | `21.10.64` | `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` → newest `21.10.*` is `21.10.64` (the only one without a `-beta` suffix) |
| `loader_version` | `0.19.5` | `https://meta.fabricmc.net/v2/versions/loader/1.21.10` → newest loader for 1.21.10 is `0.19.5`, intermediary `1.21.10` |
| `fabric_api_version` | `0.138.4+1.21.10` | `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` → newest `+1.21.10` is `0.138.4+1.21.10` |
| `architectury_api_version` | `19.0.1` | see below — **this is the one that needed a decision** |
| `dev.architectury.loom` | `1.17.493` | newest published: `https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml` |
| `net.neoforged.moddev` | `2.0.148` | newest published: `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/` |
| Gradle wrapper | `9.4.1` | inherited from the 1.21.11 tree unchanged |
| Java toolchain | 21 | `mc12110.json` → `javaVersion.majorVersion = 21` |
| `org.gradle.jvmargs` | `-Xmx2G` | local constraint (three Gradle daemons in parallel), not a version decision |

### Architectury API — why 18.0.8 and not 19.0.1

The brief's calibration table maps `architectury-fabric 19.0.1` to `~1.21.11` and `18.0.8` to
`~1.21.7`, and warns to check `fabric.mod.json` rather than the version number. Done, by downloading
the jars and reading `fabric.mod.json`:

| architectury-fabric | `depends.minecraft` |
|---|---|
| 16.1.4 | `~1.21.4-` |
| 17.0.6 | `~1.21.6` |
| 18.0.8 | `~1.21.7` |
| 19.0.1 | `~1.21.11` |
| 20.0.10+ | `>=26.1` (the next naming scheme, not applicable) |

Semver `~1.21.11` is `>=1.21.11 <1.22.0`, so **19.0.1 does not match 1.21.10**; `~1.21.7` is
`>=1.21.7 <1.22.0`, which does. Neither is a perfect match, so the choice is "closest line, and does
it actually work":

* **Fabric side:** 19.0.1 declares `minecraft: ~1.21.11`, which 1.21.10 fails. Fabric Loader would
  refuse to load it, so 19.0.1 is not an option on Fabric.
* **NeoForge side:** both 18.0.8 and 19.0.1 declare `versionRange = "[1.21.4,)"` and
  `neoforge [21.0.110-beta,)`, so both are fine there.

Since one codebase ships both loaders, **18.0.8** is the only version that loads on both for a
1.21.10 client: its `~1.21.7` range covers 1.21.10, and everything ParCool uses out of Architectury
(`DeferredRegister`, `KeyMappingRegistry`, `EntityRendererRegistry`, the event bus,
`NetworkManager`, `ClientTooltipComponentRegistry`) has the same shape in 18.0.8 as in 19.0.1 —
checked with `javap` on both jars for the classes that matter, including
`KeyMappingRegistry.register(KeyMapping)` and `ColorHandlerRegistry` (which has only the two
`registerBlockColors` overloads in 18.0.8, i.e. `registerItemColors` is already gone — see §3).

This is a real deviation from the 1.21.11 tree and from the brief's calibration table, so it is
recorded here and in `gradle.properties`. The mod metadata follows it: `architectury: ">=18.0.0"` in
`fabric.mod.json` and `versionRange = "[18.0.0,)"` in `neoforge.mods.toml`.

---

## 2. Version deltas — which side is 1.21.10 on (phase 3)

Every row below was decided from the **1.21.10 mojmap jar**, not from the version number. The jars
used are the ones Architectury Loom resolved into
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.10-loom.mappings.*/`
(one exists for 1.21.1, 1.21.2, 1.21.4 … 1.21.11, so every row could be cross-checked against the
other versions rather than guessed).

Two measurements first, because they decide most rows:

* **The class list of 1.21.10 is byte-for-byte identical to 1.21.9's** (`comm` over the two
  `unzip -l` listings: 0 new, 0 gone). 1.21.10 is therefore the same API shape as 1.21.9, and the
  1.21.9-era ports (1.21.5–1.21.7) are the better reference than the 1.21.11 tree.
* `SharedConstants` (javap `-p -constants`):

  | | `RESOURCE_PACK_FORMAT` | `DATA_PACK_FORMAT` |
  |---|---|---|
  | 1.21.7 | `64` (single int) | `81` (single int) |
  | 1.21.9 | `69.0` | `88.0` |
  | 1.21.10 | `69.0` | `88.0` |
  | 1.21.11 | `75.0` | `94.1` |

### The table

| Area | 1.21.1 / 1.21.2–1.21.7 side | 1.21.11 side | **1.21.10 is** | How it was decided |
|---|---|---|---|---|
| `ResourceLocation` vs `Identifier` | `net.minecraft.resources.ResourceLocation` | `Identifier` | **`ResourceLocation`** | `unzip -l`: `Identifier.class` present only in 1.21.11 |
| `PlayerModel` package | `net.minecraft.client.model.PlayerModel` | `net.minecraft.client.model.player.PlayerModel` | **`client.model`** | `net/minecraft/client/model/player/` exists in 1.21.11 only |
| `Parrot` package | `world.entity.animal.Parrot` | `world.entity.animal.parrot.Parrot` | **`world.entity.animal`** | javap on `net.minecraft.world.entity.animal.Parrot` succeeds |
| Render types | `RenderStateShard` + `RenderType.create(String,int,RenderPipeline,CompositeState)` + `RenderPipelines#MATRICES_FOG_SNIPPET` | `rendertype.RenderSetup` | **1.21.7 shape** | `net/minecraft/client/renderer/rendertype/` exists in 1.21.11 only; `RenderStateShard` in 1.21.10; `RenderPipelines#MATRICES_FOG_SNIPPET` present in 1.21.6+ but not 1.21.5 |
| Entity rendering | `EntityRenderer#submit(...)` in 1.21.9+; the *rope* renderer still uses `render(state, pose, MultiBufferSource, light)` on 1.21.7 | `submit(...)` with `SubmitNodeCollector` | **`submit(...)`** | javap `EntityRenderer`: 1.21.7 `render`, 1.21.9/1.21.10/1.21.11 `submit` |
| `AvatarRenderer` | absent on 1.21.7 (`PlayerRenderer` + `PlayerRenderState`) | present | **present** | `net/minecraft/client/renderer/entity/player/AvatarRenderer.class` in 1.21.9/1.21.10/1.21.11, not 1.21.7. So the 1.21.11 *avatar* mixins apply |
| `Camera` accessors | `getXRot()` / `getYRot()`, `setup(BlockGetter,…)`, `getUpVector()` / `getLeftVector()` | `xRot()` / `yRot()`, `setup(Level,…)`, `upVector()` / `leftVector()` | **1.21.7 shape** | javap `net.minecraft.client.Camera` |
| `Screen#resize` | `resize(Minecraft,int,int)` | `resize(int,int)` | **`resize(Minecraft,int,int)`** | javap `Screen` |
| `Screen` key/mouse events | `keyPressed(int,int,int)`, `mouseClicked(double,double,int)` | `KeyEvent` / `MouseButtonEvent` | **1.21.11 shape** | javap `GuiEventListener`: the event-object overloads arrive in 1.21.9 |
| `Entity#isInWaterOrBubble` | absent since 1.21.5 | absent | **absent** | javap; so `EntityUtil.isInWaterOrBubble` stays |
| `Player#canInteractWithEntity` | present | absent | **present** | javap `Player` |
| `Entity#hurt` | `final`, plus `hurtServer` | same | **same** | javap `Entity`, identical 1.21.2…1.21.11 |
| `jumpFromGround` | on `LivingEntity` since 1.21.2 | on `LivingEntity` | **on `LivingEntity`** | javap `Player`/`LivingEntity` |
| Entity / BlockEntity save | `ValueInput` / `ValueOutput` | same | **same** | javap `Entity` |
| `BlockEntityType` construction | private ctor, no `Builder` | private ctor, no `Builder` | **same as both** | javap; the 1.21.11 mixin-invoker route works unchanged |
| `Item` description id | `Item$Properties#useBlockDescriptionPrefix()` exists | same | **same** | javap `Item$Properties`; so `useBlockDescriptionPrefix()` is still needed and the `item.parcool.*` lang keys must be **removed** again |
| Attribute registration | — | `NeoForgeAttributes` on the mod event bus | **same as 1.21.11** | NeoForge 21.10 freezes `BuiltInRegistries` like 21.11 does; `Attributes#registerAll()` stays Fabric-only |
| `KeyMapping` category | `String` | `KeyMapping.Category` record | **`Category` record** | javap `KeyMapping$Category` present in 1.21.10; `register(ResourceLocation)` |
| `KeyMapping.MAP` | `Map<Key, KeyMapping>` | `Map<Key, List<KeyMapping>>` | **multi-mapping** | javap `-p` on `KeyMapping`: `private static final Map<Key, List<KeyMapping>> MAP`. So `restoreVanillaBindings` stays deleted |
| Translation keys | `key.categories.parcool` | `key.category.parcool` | **`key.category.parcool`** | follows from the `Category` record (`id.toLanguageKey("key.category")`) |
| Recipe ingredients | string form | string form | **string form** | see §2.1 |
| Recipe `category` | required | required | **required** | see §2.1 |
| `pack.mcmeta` | `pack_format: 81` + `supported_formats: [64, 81]` | `min_format`/`max_format`/`supported_formats` | **`min_format`/`max_format`, no `supported_formats`** | see §2.2 |
| NeoForge attribute lookup | `Registry#registerForHolder` from the common entry point | holder resolved, written on the NeoForge side | **1.21.11 shape** | NeoForge 21.10 closes the registries before mod constructors |
| `Commands.LEVEL_GAMEMASTERS` | `int`, `commandSource.hasPermission(int)` | `PermissionCheck` object | **`int`** | javap `Commands`: `public static final int LEVEL_GAMEMASTERS` |
| `BlockBehaviour.Properties#noCollision` | `noCollission()` (typo) on 1.21.7 | `noCollision()` | **`noCollision()`** | javap `BlockBehaviour$Properties`; the typo was fixed between 1.21.7 and 1.21.10 |
| `ArgumentTypeInfos#register` | private static | private static | **private static** | javap; access widener still needed |
| `LevelResource` ctor | private | private | **private** | javap |
| `Entity#damageSources` | public | public | **public** | javap — the 1.21.7 access widener's comment about its "return type is not" is stale; it is public in 1.21.10 |
| `KeyMapping#key` field | `private` on 1.21.7 | `protected` | **`protected`** | javap `-p` |
| `InputConstants#isKeyDown` | `(long,int)` | `(Window,int)` | **`(Window,int)`** | javap |
| `Screen#hasControlDown` / `hasAltDown` | present | absent | **absent** | javap; so the 1.21.11 `InputConstants.isKeyDown(window, …)` form is required |
| `Player#causeExtraKnockback` | absent (`attack`) | present | **absent** | javap `Player`; the `@WrapWithCondition` target stays `attack` |
| `AbstractSoundInstance` | `getLocation()` | `getIdentifier()` | **`getLocation()`** | javap `AbstractSoundInstance` |
| `EntityRenderer` rope geometry | `MultiBufferSource` | `SubmitNodeCollector#submitCustomGeometry` | **`submitCustomGeometry`** | follows from `submit(...)` |
| `RenderTypes` registration timing | no explicit `register()` | no explicit `register()` | **`register()` needed** | `ShaderManager` precompiles `RenderPipelines#getStaticPipelines()` at resource reload in 1.21.6+, see the 1.21.7 tree |
| `ZiplineInfo.CODEC`, `ZiplineType.CODEC` | present | deleted | **deleted** | they were dead code; not brought back |

### 2.1 Recipe JSON — executed against the real 1.21.10 codec

The brief's "known bug" about the missing `category` field was checked rather than assumed, by
compiling a throwaway probe against the resolved 1.21.10 jar and running the real
`RecipeSerializer` codecs:

* `ShapedRecipe$Serializer`'s codec is built with
  `CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)`.
  `MapCodec#orElse` supplies the default, so **`category` is optional on 1.21.10**, and so is
  `group`. Verified by running the codec:
  `{"type":"minecraft:crafting_shaped","pattern":["a"],"key":{"a":"minecraft:iron_ingot"},"result":{...}}`
  parses **OK** without a `category`. `ShapelessRecipe$Serializer` is the same
  (`fieldOf("category").orElse(…)`).
  **The brief is wrong on this point for 1.21.10** (and, on the same reading, for 1.21.11 — the
  `orElse` is present there too). `category` is still added, because it is what upstream ParCool
  ships and what every other port in this family has, but it is *not* load-bearing here and the
  claim that omitting it silently breaks every recipe is not reproducible on 1.21.10.
* Ingredient form: `"minecraft:iron_chain"` (string) and `"#minecraft:logs"` (tag) both parse.
  The object form `{"item": "…"}` does **not** — `Ingredient.CODEC` is
  `Item.CODEC → HolderSetCodec.create(…, allowEmpty=false) → ExtraCodecs.nonEmptyHolderSet`, i.e.
  a holder-set codec, and `{"item": …}` is not a holder set. So the 1.21.11 tree's string form is
  correct for 1.21.10 as well.
* **`minecraft:iron_chain` exists on 1.21.10 and `minecraft:chain` does not.** Checked two ways:
  `unzip -l` of the 1.21.10 client jar shows `data/minecraft/recipe/iron_chain.json`,
  `assets/minecraft/items/iron_chain.json`, `data/minecraft/loot_table/blocks/iron_chain.json`
  and no `chain.*` of any kind; and running `BuiltInRegistries.ITEM.containsKey` gives
  `iron_chain -> true`, `chain -> false`. The brief's guess that 1.21.10 might not have it, and
  that `minecraft:chain` is the right value here, are both wrong — the 1.21.11 tree's
  `minecraft:iron_chain` is correct and is kept.
* `#minecraft:logs` is fine. It looks like a block tag, but 1.21.10 ships
  `data/minecraft/tags/item/logs.json` too (→ `#minecraft:logs_that_burn`, `#minecraft:crimson_stems`,
  `#minecraft:warped_stems`), and an ingredient resolves against the *item* registry. The probe's
  first "Missing tag: 'minecraft:logs' in 'minecraft:item'" was an artefact of the probe itself —
  a bare `Bootstrap.bootStrap()` populates no tags at all — not a defect in the recipe. Re-run with a
  vanilla item substituted for the tag it parses cleanly, and with the tag present it parses too.

### 2.2 `pack.mcmeta` — executed against the real 1.21.10 codec

The field set is *not* the 1.21.11 one. `PackMetadataSection.forPackType(PackType).codec()` was run
over candidate files on 1.21.10 (both pack types, since a mod jar is loaded as both):

| candidate | `CLIENT_RESOURCES` | `SERVER_DATA` |
|---|---|---|
| the 1.21.11 base content (`pack_format: 81` + `min_format`/`max_format` + `supported_formats`) | **ERR** `supported_formats is deprecated starting from pack format 65` | OK |
| `pack_format: 81` alone | **ERR** `declares support for version newer than 64, but is missing mandatory fields min_format and max_format` | OK |
| `min_format: 64, max_format: 88` | **ERR** `declares support for format 64, but … formats 17 to 64 require a supported_formats field` | **ERR** `… formats 17 to 81 require a supported_formats field` |
| `min_format: 69, max_format: 88` | OK | **ERR** (same: 69 ≤ 81) |
| `min_format: 69, max_format: 88` + `supported_formats` | **ERR** (deprecated from 65) | **ERR** (`require a pack_format field`) |
| `min_format: 82, max_format: 88` | **OK** | **OK** |
| `min_format: 88, max_format: 88` | OK | OK |

So on 1.21.10: **`min_format` + `max_format`; `supported_formats` must be gone** (deprecated from
resource format 65 / data format 82 and a hard parse error when present), and **`min_format` has to
be ≥ 82**, which is `PackFormat.lastPreMinorVersion(SERVER_DATA)` (javap: the `PackType` switch
returns `bipush 64` for `CLIENT_RESOURCES` and `bipush 81` for `SERVER_DATA`). The 1.21.11 tree's
file, copied verbatim, does not even parse as a resource pack on 1.21.10.

Shipped:

```json
{
    "pack": {
        "description": "ParCool mod resources",
        "min_format": 82,
        "max_format": 88
    }
}
```

`88` is `SharedConstants.DATA_PACK_FORMAT_MAJOR` on 1.21.10 (`69`/`88`, from
`javap -p -constants`). Verified by re-running the codec against the file as it actually comes out
of the built jar: `CLIENT_RESOURCES -> parses OK`, `SERVER_DATA -> parses OK`.

The one thing that is *not* clean is `PackCompatibility`: a range of `[82, 88]` is `TOO_NEW` against
the resource format `69.0`. There is no range that is simultaneously `COMPATIBLE` with both `69.0`
and `88.0` **and** parses under both codecs — `[64, 88]` and `[69, 88]` are compatible with both but
are rejected by the `SERVER_DATA` codec, exactly as the table above shows. That is inherent to 1.21.10
having a single `pack.mcmeta` for a jar that is both a resource and a data pack; every 1.21.10 mod
faces it, and `TOO_NEW` only decorates the pack name with " (incompatible)" in `PackRepository`'s
listing (`method_59808`) rather than refusing to load it, while a codec failure makes
`Pack.readPackMetadata` return `null` and the pack is dropped outright. Parsing is therefore the
property that has to hold, and it does.

### 2.3 Things the 1.21.11 tree gets right that must NOT be walked back

The base tree is not wrong everywhere; these are 1.21.9-or-earlier changes that it carries and that
this port keeps:

* `Properties#setId` on both `Blocks` and `Items`. `Item$Properties#effectiveDescriptionId` and
  `effectiveModel` both start with `Objects.requireNonNull(this.id, "Item id not set")` on 1.21.10
  (javap -c), and Architectury's `DeferredRegister` does not set it.
* `Item$Properties#useBlockDescriptionPrefix()`. It exists on 1.21.10, and the description id is
  resolved from the properties alone (`BlockItem#getDescriptionId` is gone), so without the call the
  hooks would be `item.parcool.*` rather than `block.parcool.*` and every lang file would be one key
  off. The `item.parcool.*` entries the 1.21.11 tree added to eleven lang files were therefore
  removed again here.
* `EntityUtil.isInWaterOrBubble`. `Entity#isInWaterOrBubble` is gone on 1.21.10 (javap).
* `Attributes.registerAll()` called only from the Fabric entry point, with `:neoforge`'s
  `NeoForgeAttributes` on the mod event bus. Confirmed against the NeoForge 21.10.64 *sources* jar:
  `net/neoforged/neoforge/registries/DeferredRegister.java` has no `Attributes` nested class and
  `NeoForgeRegistries` has no `ATTRIBUTE` entry, i.e. the generic `DeferredRegister.create(…)` the
  port uses is the only option there, exactly as on 21.11.
* `BlockEntityTypeInvoker` + `ParCoolPlatform#registerBlockEntityType`. `BlockEntityType`'s private
  `(BlockEntitySupplier, Set)` constructor is unchanged on 1.21.10 (javap).
* `KeyboardInput#tick()` taking no arguments — the handler must be `tick()V` with a bare
  `CallbackInfo`.
* `ZiplineRopeEntity#addAdditionalSaveData` writing six distinct keys.

---

## 3. Bugs found

### Fixed

| # | Bug | Where | Evidence / fix |
|---|---|---|---|
| 1 | `@WrapWithCondition(method = "causeExtraKnockback", …)` targets a method 1.21.10 does not have | `common/…/mixin/common/PlayerMixin.java` | `Player#causeExtraKnockback` only exists from 1.21.11; javap on 1.21.10 shows `attack(Entity)` and no `causeExtraKnockback`. With `defaultRequire: 1` this is a hard boot failure. Retargeted to `attack`, whose `setSprinting(Z)` call is the same one (javap -c: `invokevirtual setSprinting` at offset 636, inside `attack`, behind the same knockback-is-nonzero test). Caught by `tools/verify_mixins.py`. |
| 2 | `pack.mcmeta` copied from the 1.21.11 tree does not parse on 1.21.10 | `common/src/main/resources/pack.mcmeta` | The 1.21.11 file carries `supported_formats`, which 1.21.10's `PackMetadataSection` codec rejects outright (`supported_formats is deprecated starting from pack format 65`). Replaced with `min_format: 82, max_format: 88`, verified by running the real codec against the file as it comes out of the built jar. |
| 3 | The five recipe JSONs have no `category` | `common/src/main/resources/data/parcool/recipe/*.json` | Added `"category": "misc"` to all five. **Note this is not load-bearing on 1.21.10** — see §2.1; the brief's claim is not reproducible. It is added because it is what upstream ParCool and every sibling port ship. |
| 4 | `pack.mcmeta` from the 1.21.7 tree (`pack_format: 81, supported_formats: [64, 81]`) also fails on 1.21.10 | same | `pack_format` alone is rejected for format > 64 under `CLIENT_RESOURCES`. Both reference trees' values are wrong here; §2.2 has the codec output. |
| 5 | Paraglider pinned to a 1.21.11 build | `neoforge/build.gradle` | `Paraglider-neoforge-21.11.0-beta.6` (7794499) references `net.minecraft.resources.Identifier`, which does not exist on 1.21.10; `:neoforge:compileJava` failed with `cannot access Identifier`. CurseForge has **no** Paraglider build for 1.21.10 at all (checked `https://www.curseforge.com/api/v1/mods/289240/files`: the list goes 21.1.x → 21.5.x → 21.11.0-beta). Pinned to 21.5.2 (6739612), whose `MovementPlugin$PlayerStateRegister` / `$PlayerStateConnectionRegister` signatures are identical (javap on both jars). |

### Found, not fixed

| # | Finding | Why not fixed |
|---|---|---|
| 1 | `ZiplineRopeEntity` and other entities carry `Attribute` modifiers keyed by `ResourceLocation`; nothing breaks, but the ids are never removed when the action stops | upstream behaviour |
| 2 | `#minecraft:logs` in `wooden_zipline_hook.json` is a tag that has to be resolved as an item tag. 1.21.10 *does* ship `data/minecraft/tags/item/logs.json`, so this is correct — recording it because a naive reading of the recipe makes it look like a bug | not a bug |
| 3 | 27 unused imports, byte-identical to the 1.21.11 tree's list | upstream; the brief says not to touch them. Verified with a per-file scan that the *set* is unchanged from the base tree, so this port added none. |
| 4 | The read-only trees `parcool-Architectury-API-1.21.11` and `parcool-Architectury-API-1.21.1` both contain `ConfigSpec#persist` that writes nothing and a `BufferUtil.ensureRoom` that checks nothing. This port takes the **1.21.1** versions of both files. | the read-only trees are not to be edited. Recording it so the next port does not copy them. |
| 5 | A single `./gradlew build` on a clean checkout fails | Architectury Loom resolves `:common` at *configuration* time of `:fabric`. Inherited from the base, documented in BUILDING.md rather than patched. |
| 6 | Neither reference tree has the filename validation in `Limitation` that the brief mentions | checked `Limitations`' `ID` record across all seven trees: there is none to preserve. |

---

## 4. What the next port should not trust

1. **A version number.** Every decision in §2 came from `javap` against the resolved jar. The class
   list of 1.21.10 is identical to 1.21.9's, which is the single most useful fact about it: the
   1.21.5–1.21.7 trees are a far better reference than the 1.21.11 tree, even though 1.21.11 is the
   nearer neighbour by number.
2. **A regex over an annotation.** See §5.
3. **`minecraft-merged-*-sources.jar` in the Loom cache.** It is generated *with the access widener
   already applied*, so it lies about visibility. `javap -p` against the mojmap jar is the truth.
4. **`pack.mcmeta` copied from a sibling port.** Every one of the five sibling values is rejected by
   1.21.10's codec — including the two reference trees'. It has to be derived per version.
5. **The brief's recipe claim.** `category` is optional on 1.21.10 *and* on 1.21.11 (the codec is
   `fieldOf("category").orElse(MISC)` in both). "Every ParCool recipe silently fails without it" is not
   true for either version; the field is still shipped, but for upstream fidelity, not as a fix.
6. **The brief's `iron_chain` claim.** `minecraft:iron_chain` exists on 1.21.10 and
   `minecraft:chain` does not — the 1.21.11 tree's value is right and the 1.21.2/1.21.4 trees'
   `{"item": "minecraft:chain"}` is wrong. The object form also does not parse at all: `Ingredient.CODEC`
   is a holder-set codec.

---

## 5. The mixin verifier, and why it self-tests

`tools/verify_mixins.py` checks 46 injection/field targets across 233 files. It exists because the
brief records that a previous verifier in this family parsed annotations with a regex like
`\(([^)]*)\)`, which truncates at the first `)` — i.e. inside the first method descriptor — so it
skipped every target that had one and reported "0 problems" about an entirely broken tree.

Three things guard against a repeat:

* descriptors are extracted with **balanced parentheses**, and the method name is the identifier
  immediately before the argument list, not the last whitespace-separated token (which breaks on a
  generic signature such as `extractRenderState(AvatarlikeEntity, Foo, float)` — the `AvatarRenderer`
  case here);
* `@At(target = "Lowner;name(desc)")` is checked too, with a supertype fallback, because the owner in
  an invoke's constant pool is the receiver's static type, not the declaring class
  (`Player.setSprinting` is really `Entity#setSprinting`);
* **every run first executes `self_test()`**, which feeds the same code path a wrong-arity target, a
  wrong-name target and a genuinely correct 1.21.10 target, and refuses to report anything unless the
  first two are flagged and the third stays silent.

It found exactly one real defect in this port: bug #1 above, the `causeExtraKnockback` target.

---

## 6. Verification status — what was checked and what was not

**Checked, by running the real thing:**

* `./gradlew :common:build` then `./gradlew build` from a deleted `build/` + `.gradle/` — both jars
  produced.
* `./gradlew :common:checkCommonLoaderIndependence` — passes.
* `tools/verify_mixins.py` — self-test passes, 46 targets checked, no problems.
* **Fabric jar**: bytecode is intermediary (`net/minecraft/class_2498`, …), the access widener is
  `accessWidener v2 intermediary`, there is **no refmap**, the mixin targets are statically remapped
  (`method=["method_7324"]`, `target="Lnet/minecraft/class_1657;method_5728(Z)V"`), the
  `ServiceLoader` binding is present, `fabric.mod.json` is expanded to the real version.
* **NeoForge jar**: bytecode is mojmap (`net/minecraft/resources/ResourceLocation`, …), the mixin
  targets are mojmap (`method=["attack"]`), `accesstransformer.cfg` and `parcool.accesswidener` are
  both shipped, and the loader-specific classes plus the four integrations are in it.
* **`pack.mcmeta`** run through 1.21.10's real `PackMetadataSection` codec: parses under both
  `CLIENT_RESOURCES` and `SERVER_DATA`.
* **The four vanilla-serialiser recipes** run through 1.21.10's real `RecipeSerializer` codecs:
  all parse. Also established: `category` is optional, and the object ingredient form does not parse.
* **Registry contents** (`BuiltInRegistries.ITEM.containsKey`): `iron_chain` exists, `chain` does
  not.
* No `System.out`, no `printStackTrace`, no absolute machine path in any build file, no
  `org.gradle.java.home`, no unused imports beyond the base tree's 27.

**Not checked — the game was never launched.** No `runClient`, no `runServer`, no Prism instance.
So all of the following are unverified on 1.21.10 and rest on API-level evidence only:

* that the game starts on either loader and the mod shows no failed mod state;
* that the attributes resolve on the first `Player#createAttributes` — the split is inferred from
  the NeoForge 21.10.64 sources and the absence of a `DeferredRegister.Attributes`, never executed;
* that every key binding rebinds and drives its action, and that a vanilla key ParCool also binds
  still works (the multi-mapping `KeyMapping.MAP` says it should; unconfirmed);
* every action of every family (wall run, wall jump, slide, roll, dodge, vault, hide-in-block, zipline
  ride), the stamina HUD and the settings screen;
* that the zipline rope actually draws — `RenderTypes` builds its own pipelines and registers them in
  `Renderers#register()` precisely so they exist before `ShaderManager` precompiles; that reasoning is
  unverified;
* two clients on one server seeing each other's animations;
* that the recipes are craftable in game (the codecs accept them, but `category` in the recipe book
  and the creative tab are cosmetic and unconfirmed).

## 7. The decode ran on a released buffer — `refCnt: 0`

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

## 7. The decode ran on a released buffer — `refCnt: 0`

Published artifacts after the fix (copied to `/home/sanufsoii/ports/готовые порты/parcool/`):

```
0.1-mc1.21.10fabric-3.4.3.3.jar
  1 207 726 bytes, 516 entries, 350 classes
  sha256 c51caa76ff726e1e6d53735c7082c6a3b6d107e042a546b2378db2247471b5b5
0.1-mc1.21.10neoforge-3.4.3.3.jar
  1 224 499 bytes, 533 entries, 360 classes
  sha256 7d699599318696a5f0e1df84466eb9eae013e488af8f29112b1d9696cc4258db
```

Not byte-for-byte identical to the pre-fix pair
(`ff53f9ecfba3d0b2df7337c6c902a8cb7ddbeef255273279deded7efe5c3a233`, 1 207 689 bytes, and
`0cf4908a62fd583a6a6a53ef7d79e2b04fe6ff3c7b6f9e8cc749dd97c83c1939`, 1 224 517 bytes): +37 and
−18 bytes. Entry and class counts are unchanged (516/350 and 533/360), so the only changed content
is the body of the receiver lambda in `FabricParCoolNetwork#register` and
`NeoForgeParCoolNetwork#register`.

**The game was not launched for this fix, on either loader**, so none of the runtime behaviour
claimed in §6 changed status: the network now decodes on a live buffer by construction and in the
bytecode, but that a ParCool packet now actually round-trips has not been observed.

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

---

## Dedicated server: verified. The `AbstractMethodError` defect does **not** exist on 18.0.8

The brief said this port had the same defect as 1.21.4 / 1.21.8 — a dedicated Fabric server dying at
mod init with `AbstractMethodError` on `Adaptor#registerS2C`, because Fabric Loader's
`EnvironmentStripper` deletes a member annotated `@Environment(EnvType.CLIENT)`. **It was checked
here and it is not present.** The check is the finding.

### `javap` on the two Architectury versions, side by side

`javap -v -p` on `dev/architectury/networking/fabric/NetworkManagerImpl$1.class`:

| architectury-fabric | `registerS2C` carries `RuntimeInvisibleAnnotations: EnvType.CLIENT`? |
|---|---|
| `17.0.8` (the 1.21.8 port) | **yes** |
| `18.0.8` (this port) | **no** |

On 17.0.8 the annotation is there, the member is stripped on a dedicated server, the class stops
implementing `NetworkAggregator$Adaptor`, and the first `registerS2CReceiver` call throws. On 18.0.8
the annotation is gone and Architectury moved the side split *into the method body* (`javap -c`):

```
  0: invokestatic  PayloadTypeRegistry.playS2C()
  5: invokeinterface PayloadTypeRegistry.register   // <- payload type registered on BOTH sides
 11: invokestatic  Platform.getEnvironment()
 14: getstatic     Env.CLIENT
 17: if_acmpne     26                               // <- client-only receiver registration
 20: aload_1
 21: aload_2
 22: aload_3
 23: invokestatic  ClientNetworkManagerImpl.registerS2C
 26: return
```

Nothing is stripped, nothing is unimplemented, and `NetworkManager.registerReceiver(S2C, id, receiver)`
is safe on a dedicated server. **This is a different situation, not a milder version of the same
failure.** 18.0.5 (the 1.21.9 port) is byte-for-byte the same shape, so the two ports agree.

This also settles the brief's second question — running a **1.21.7-built** Architectury on 1.21.10
is *not* causing a server-side failure. 18.0.8 was chosen because 19.0.1 declares `~1.21.11` and does
not apply (see §1), and that choice costs nothing here.

### Evidence: the dedicated server booted on the **pre-fix** code

Before touching `FabricParCoolNetwork.java`, a plain `./gradlew :fabric:runServer`:

```
577:[12:17:19] [Server thread/INFO] (Minecraft) Starting Minecraft server on *:25565
618:[12:17:21] [Server thread/INFO] (Minecraft) Done (1.531s)! For help, type "help"
```

No `AbstractMethodError`. Log kept at `/tmp/opencode/srv-1.21.10-BEFORE.log`. Without this, the
post-fix `Done (` below would prove nothing.

### The guard, applied anyway

`fabric/src/main/java/com/alrex/parcool/platform/FabricParCoolNetwork.java` now starts `register`
with:

```java
if (clientbound && Platform.getEnvironment() == Env.SERVER) {
    NetworkManager.registerS2CPayloadType(wireId);
    return;
}
```

On this port that switch is **behaviourally a no-op on a dedicated server** — write it up that way
rather than claiming a crash was fixed. `javap -c` on `NetworkAggregator` shows `registerS2CType` and
`registerS2CReceiver` fill `S2C_TYPE`, `S2C_CODECS` and `S2C_TRANSFORMERS` with the same values, and
both end at `PayloadTypeRegistry.playS2C().register(type, BufCustomPacketPayload.streamCodec(type))`;
only `S2C_RECEIVER`, which a server never reads, is left empty. It is kept for three reasons:

1. It is what Architectury's javadoc on `NetworkManager#registerS2CPayloadType` prescribes. Relying on
   an upstream accident rather than the documented contract is exactly what left the 17.x ports one
   version bump away from crashing at mod init.
2. It keeps the same code as 1.21.2, 1.21.8 and 1.21.9 — the compiled `register` body is identical
   across all four, only the comments and the class javadoc differ — so the next port copy does not
   have to work out which shape it inherited.
3. If Architectury restores the annotation, or a future bump lands on a 17.x-shaped line, the port is
   already on the safe path.

`NetworkChannel` was **not** used instead: `javap -c` on `NetworkChannel#register` shows it wraps the
S2C registration in `if (Platform.getEnvironment() == Env.CLIENT)`, so on a dedicated server only the
C2S half of the aggregator is populated and every server-to-client packet NPEs on
`S2C_TYPE.get(id) == null`. A green `Done (` would have hidden that.

The correct decode order was **not** touched: the payload is still decoded before `context.queue(...)`,
because `registerReceiver` releases the buffer as soon as the receiver lambda returns, and decoding
inside the queue would read freed memory (`IllegalReferenceCountException: refCnt: 0`). See *The
decode ran on a released buffer*.

### Proof, from the log after the fix

Clean Loom cache (`.gradle/loom-cache`, `fabric/build/loom-cache`, `common/build/loom-cache`,
`common/build/devlibs`, `neoforge/build/explodedCommon`), then `./gradlew :fabric:runServer`:

```
600:[12:34:08] [Server thread/INFO] (Minecraft) Starting Minecraft server on *:25565
630:[12:34:09] [Server thread/INFO] (Minecraft) Done (0.294s)! For help, type "help"
```

`grep -c AbstractMethodError` on `/tmp/opencode/srv-1.21.10.log`: **0**.

### Warning to the next port

**`@Environment(EnvType.CLIENT)` stripping is a defect class that the compiler cannot see, a client
cannot see, and single player cannot see. Only a dedicated server catches it. If you touch Fabric
networking registration, boot `:fabric:runServer` and look for `Done (`. A client run is not a
substitute and a green build is not a substitute.**

And check which side of Architectury's own fix you are on before assuming you have this bug:
`javap -v` on `NetworkManagerImpl$1`, look for the `Environment` annotation on `registerS2C`. It is
present through the 17.x line (14.0.4, 15.0.3, 16.1.4, 17.0.6, 17.0.8) and gone from 18.0.5 onward.
The `javap` costs a second; the mistaken port costs a build cycle and a wrong note in the tree.

### Artifacts after this change

| file | sha256 | vs. before |
|---|---|---|
| `parcool-1.21.10-3.4.3.3-fabric.jar` | `acd406d271075f216e9e5c9768792b16b39bf5176372165dace83c2c08b8ecc9` | changed from `c51caa76ff726e1e6d53735c7082c6a3b6d107e042a546b2378db2247471b5b5` — the edit is in the Fabric module, so it must change |
| `neoforge/build/libs/parcool.jar` | `7d699599318696a5f0e1df84466eb9eae013e488af8f29112b1d9696cc4258db` | **unchanged** — confirms the fix is Fabric-specific and touched neither `common/.../platform/ParCoolNetwork.java` nor the NeoForge module |

Published to `/home/sanufsoii/ports/готовые порты/parcool/` as
`0.1-mc1.21.10fabric-3.4.3.3.jar` and `0.1-mc1.21.10neoforge-3.4.3.3.jar`.
`:common:checkCommonLoaderIndependence`, `:common:build` and `build` are all green.

### Still unverified

* **The client was not launched** — the orchestrator holds the single GPU. The client path is
  untouched by the edit, but "the client still boots and still receives S2C packets" is *not*
  observed on this port.
* **A real S2C packet was never delivered by a live player.** The server was started empty, so
  `NetworkAggregator.S2C_TYPE` / `S2C_CODECS` were populated but never exercised. Real sending means
  a player joining (limitation snapshot, stamina broadcast, action state, breakfall event). A green
  `Done (` does not prove sending works.
* NeoForge's dedicated server was not launched either.

## Spectator noclip was broken on every version (found by the user in a live world)

The user could not fly through blocks in spectator mode on any port, and Ctrl+P did not help.

**Root cause: ParCool was clearing a flag vanilla owns.** `Entity#noPhysics` is the whole mechanism
behind spectator noclip - `Player#tick` contains, verified with `javap -c` on the mojmap jar:

```
 2: invokevirtual net/minecraft/world/entity/Entity.isSpectator:()Z
 5: putfield      noPhysics:Z
```

`mixin/common/EntityMixin#onMove` then had this, at the head of `Entity#move` for every player:

```java
} else if (player.noPhysics) {
    player.noPhysics = false;
}
```

That branch exists to undo an upstream leak - upstream raised `noPhysics` for an enforced move and
never gave it back, so a player who had used HideInBlock kept falling through the world. But it was
unconditional, so on the first `move` of every tick it also cleared the flag vanilla had just set for
a spectator. `Entity#move` branches on `noPhysics` as its first instruction, so with the flag gone
the player collided with the world like a survival player. No ParCool action had to be running for
this to fire, which is exactly why Ctrl+P changed nothing.

**Fix: only ever give back a flag ParCool raised itself.** `BehaviorEnforcer` now carries
`noPhysicsRaisedByParCool`, set wherever ParCool assigns `noPhysics = true` (`EntityMixin#onMove` and
both sites in `HideInBlock`) and cleared on `HideInBlock#onStop`. The cleanup branch tests that flag
instead of the shared one.

**Note on the blast radius.** `mixin/common/EntityMixin.java` was byte-identical across all nine
ports (md5 `0ec72484d9`), so the defect and the fix are uniform - which is also why it survived nine
independent ports and a full green build on every one of them. A boot check, a mixin verifier and
`checkCommonLoaderIndependence` cannot see this: the class applies, every `@Inject` resolves, and
the bug only exists in the *value* a vanilla-owned field is being assigned.

**Verified statically on the 1.21.9 reference build:** `javap -c net.minecraft.world.entity.player.Player`
shows `isSpectator()` then `putfield noPhysics` in `tick()`, and no other vanilla class writes
`noPhysics` except the three ParCool sites. **Not yet confirmed by a live spectator test** - that is
the one check that closes this, and it is the user's to run.
