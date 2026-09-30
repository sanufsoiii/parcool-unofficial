# Building ParCool (Architectury, Minecraft 1.21.9)

Requires JDK 21. The toolchain is declared through `java { toolchain { languageVersion = 21 } }` in
`build.gradle` and `org.gradle.jvmargs` in `gradle.properties`; there is no `org.gradle.java.home` in
the project and no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`): Minecraft 1.21.9, NeoForge 21.9.16-beta,
Fabric Loader 0.19.5, Fabric API 0.134.1+1.21.9, Architectury API 18.0.8, Architectury Loom 1.17.493,
ModDevGradle 2.0.148, Gradle 9.4.1. `NOTES.md` records the endpoint each number came from.

Two of those deserve a note:

* **NeoForge 21.9.16-beta** — every published NeoForge build for MC 1.21.9 carries the `-beta`
  suffix; the `21.9.x` line stops at `.16-beta` and the next entry in the metadata is `21.11.42`.
* **Architectury API 18.0.8** — decided from `architectury-fabric`'s own `fabric.mod.json`, which is
  the only signal that discriminates: the 18.x line declares `depends.minecraft = "~1.21.7"`
  (`>=1.21.7 <1.22.0`) and the 19.x line declares `~1.21.11`, which **1.21.9 does not satisfy**.
  `architectury-neoforge` declares the same wide `[1.21.4,)` / `[21.0.110-beta,)` range in every
  16.x–19.x build and therefore says nothing. Architectury published nothing between 1.21.7 and
  1.21.11, so 1.21.8 / 1.21.9 / 1.21.10 all land in the 18.x window.

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
# -> neoforge/build/libs/parcool-neoforge.jar
```

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/neoforge.mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, and the `ServiceLoader` file that binds `ParCoolPlatform`.

### Access widening is declared, not inherited

Loom's compile classpath carries the union of every dependency's access wideners, so a mod can
compile against a member that only Architectury or fabric-api widens — and then fail on a real client
where the other mod's version differs. Every member this port reaches across a package boundary is
therefore declared explicitly in `common/src/main/resources/parcool.accesswidener`, and the NeoForge
mirror of that list is trimmed to what is still private in the NeoForge
`21.9.16-beta` runtime artifact (`neoforge/build/moddev/artifacts/neoforge-21.9.16-beta.jar`):

| Member | Fabric (AW) | NeoForge (AT) |
|---|---|---|
| `Entity#onGround` | field | not needed — public on NeoForge |
| `LivingEntity#swimAmount` / `#swimAmountO` | field | not needed |
| `Player#canPlayerFitWithinBlocksAndEntitiesWhen` | method | not needed |
| `ArgumentTypeInfos#register` | shadow only | **yes** — still `private static` on 1.21.9 |
| `RenderType#create(String, int, RenderPipeline, CompositeState)` | method | not needed |
| `RenderStateShard#NO_TEXTURE` / `#LIGHTMAP` | field | not needed |
| `RenderPipelines#PIPELINES_BY_LOCATION` | field | **yes** — still `private static` |
| `RenderPipelines#MATRICES_FOG_SNIPPET` | field | not needed |

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
