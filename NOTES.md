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

### Architectury API — why 19.0.1 and not 18.0.8

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
1.21.10 client — its `~1.21.7` range covers 1.21.10, and 1.21.7→1.21.10 is API-compatible for
everything Architectury touches (verified: it configures, compiles and runs).

This is a real deviation from the 1.21.11 tree and from the brief's calibration table, so it is
recorded here and in `gradle.properties`.

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

---

## 3. Bugs found

### Fixed

### Found, not fixed

---

## 4. What the next port should not trust

TBD
