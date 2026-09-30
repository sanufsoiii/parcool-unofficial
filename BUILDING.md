# Building ParCool (Architectury, Minecraft 1.21.9)

Requires JDK 21 or newer. The toolchain is declared through
`java { toolchain { languageVersion = 21 } }` in `build.gradle`, and `org.gradle.java.home` is not
set: there is no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`): Minecraft 1.21.9, NeoForge 21.9.16-beta,
Fabric Loader 0.19.5, Fabric API 0.134.1+1.21.9, Architectury API 18.0.5, Architectury Loom 1.17.493,
ModDevGradle 2.0.148, Gradle 9.4.1. The endpoint each number came from is written down in the comment
blocks of `gradle.properties` and `settings.gradle`. Architectury API is **18.0.5**, not 18.0.8: 18.0.6
and later drag in a fabric-api that hard-requires MC 1.21.10, which Fabric Loader refuses here at
resolution time.

Two of those deserve a note:

* **NeoForge 21.9.16-beta** — every published NeoForge build for MC 1.21.9 carries the `-beta`
  suffix; the `21.9.x` line stops at `.16-beta` and the next entry in the metadata is `21.11.42`.
* **Architectury API 18.0.5** — the *line* is decided from `architectury-fabric`'s own
  `fabric.mod.json`, which is the only signal that discriminates: the 18.x line declares
  `depends.minecraft = "~1.21.7"` (`>=1.21.7 <1.22.0`) and the 19.x line declares `~1.21.11`, which
  **1.21.9 does not satisfy**. `architectury-neoforge` declares the same wide `[1.21.4,)` /
  `[21.0.110-beta,)` range in every 16.x–19.x build and therefore says nothing. Architectury
  published nothing between 1.21.7 and 1.21.11, so 1.21.8 / 1.21.9 / 1.21.10 all land in the 18.x
  window. The *version* inside that line is then settled by the fabric-api it pulls — see
  `gradle.properties`.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.moddev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, and the Paraglider / EpicFight / BetterThirdPerson / ShoulderSurfing integrations. |

## Building

```bash
./gradlew :common:build   # once on a fresh checkout
./gradlew build           # from then on
```

The two steps are needed because Architectury Loom resolves the `:common` project dependency while it
*configures* the loader modules, so `:common` has to have been built once. The root `build` task
depends on the `bootstrap` task (an alias of `:common:build`), which covers every case except a truly
fresh checkout. This is inherited from the 1.21.11 tree and is documented rather than worked around.

**If you add a new class to `:common` and the loader module then reports
`cannot find symbol` for it, Loom's cached remap of `:common` is stale.** Clear it once:

```bash
rm -rf common/build/devlibs common/build/loom-cache fabric/build/loom-cache .gradle/loom-cache
```

`.gradle/loom-cache/remapped_mods` holds the per-consumer remapped copy of `:common` that the loader
modules actually load; if a freshly added mixin class is present in the built jar but reported as
"not found" at runtime, this directory is the stale one. (Do not use `./gradlew --stop` to force it —
that stops every daemon on the machine, including other projects'.)

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.21.9-3.4.3.3-fabric.jar
# -> neoforge/build/libs/parcool-1.21.9-3.4.3.3-neoforge.jar
```

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/neoforge.mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, and the `ServiceLoader` file that binds `ParCoolPlatform`.

### Access widening is declared, not inherited

Loom's compile classpath carries the union of every dependency's access wideners, so a mod can
compile against a member that only Architectury or fabric-api widens — and then fail on a real client
where the other mod's version differs. Every member this mod reaches across a package boundary is
therefore declared explicitly in `common/src/main/resources/parcool.accesswidener`, and
`neoforge/src/main/resources/META-INF/accesstransformer.cfg` mirrors it member for member:

| Member | widened on |
|---|---|
| `Entity#onGround` | both |
| `LivingEntity#swimAmount` / `#swimAmountO` | both |
| `Player#canPlayerFitWithinBlocksAndEntitiesWhen` | both |
| `ArgumentTypeInfos#register` | both |
| `RenderType#create(String, int, RenderPipeline, CompositeState)` | both |
| `RenderStateShard#NO_TEXTURE` / `#LIGHTMAP` | both |
| `RenderPipelines#PIPELINES_BY_LOCATION` | both |
| `RenderPipelines#MATRICES_FOG_SNIPPET` | both |

Mirroring rather than trimming is deliberate. Reading the AT jar with `javap` answers the wrong
question: those bytes are the state *before* NeoForge applies its own access transformers, the classes
are transformed in memory at launch, and after that the members are private. A member that looks
public in the artifact fails the moment a player ticks, while an AT entry for an already-public
member is harmless — so the cost is asymmetric and the access widener is the authoritative list. The
header of the AT file has the full reasoning and the `IllegalAccessError` it prevents.

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runclient    :neoforge:runserver
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin. A dedicated server refuses to start until it is acknowledged, so before the first
`:fabric:runServer` / `:neoforge:runserver` create `<module>/run/eula.txt` containing:

```
eula=true
```

Those `run/` directories are generated by the dev tasks and are not checked in.

`mixin.debug` can be switched for `mixin.debug.export` (writes `<module>/run/.mixin.out/`, one
transformed class per file) when a specific mixin needs inspecting.

### Booting straight into a world / a server

The two cross-loader checks ("does a client of one loader actually join a server of the other one?",
"does the in-world render path survive a real frame?") are driven by the game's own quick-play flags,
so they can be repeated without a mouse:

```bash
# client of one loader -> dedicated server of the other one
./gradlew :neoforge:runServer                                   # waits for "Done (!)"
./gradlew :fabric:runClient -PparcoolQuickPlay=127.0.0.1:25566

# straight into a saved world, i.e. the render path with the mod's blocks in it
./gradlew :fabric:runClient  -PparcoolQuickPlayWorld="New World"
./gradlew :neoforge:runClient -PparcoolQuickPlayWorld="New World"
```

The dedicated servers need `online-mode=false` in `<module>/run*/server.properties` (offline dev
login) and a free `server-port`.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*` or `net.neoforged.*`. It is wired into `:common:check`, so `./gradlew build` runs it.
