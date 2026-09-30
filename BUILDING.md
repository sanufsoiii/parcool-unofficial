# Building ParCool (Architectury, Minecraft 1.21.5)

Requires JDK 21 or newer. The toolchain is declared through
`java { toolchain { languageVersion = 21 } }` in `build.gradle`, and `org.gradle.java.home` is not
set: there is no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`): Minecraft 1.21.5, Architectury API 16.1.4,
NeoForge 21.5.98, Fabric Loader 0.16.14, Fabric API 0.128.2+1.21.5, Architectury Loom 1.17.493,
ModDevGradle 1.0.24, Gradle 9.4.1. Each is read out of the publisher's own
`maven-metadata.xml`; the reasoning behind the two non-obvious ones (Architectury API 16.1.4 and
Fabric API 0.128.2) is in the comment block above `architectury_api_version` and `fabric_api_version`
in `gradle.properties`.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.moddev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, and the Paraglider / EpicFight / BetterThirdPerson integrations. |

Loom 1.17 is not a choice — `architectury-plugin` 3.5.170 is the newest published and calls
`LoomGradleExtension#disableObfuscation()`, which only exists in Loom 1.17.x. Gradle 9.4.1 is that
Loom's minimum, and 9 is also what forces `neoforge/build.gradle` to spell out the two Mojang-side
repositories that ModDevGradle's own `RepositoriesPlugin` would otherwise inject. Both facts are
explained in `settings.gradle` and in the `neoforge/build.gradle` repository block.

## Building

```bash
./gradlew :common:build   # on a fresh checkout
./gradlew build
```

The two steps are needed because Architectury Loom resolves the `:common` project dependency while it
*configures* the loader modules, so `:common` has to have been built once. The root `build` task
depends on the `bootstrap` task (an alias of `:common:build`), which covers every case except a truly
fresh checkout. The symptom of skipping the first step is

```
A problem occurred configuring project ':fabric'.
> Failed to setup Minecraft, java.io.UncheckedIOException: Failed to read metadata from
  …/common/build/libs/parcool-1.21.5-3.4.3.3.jar, java.nio.file.NoSuchFileException
```

This is inherited from Architectury Loom and is documented rather than fixed.

**If you add a new class to `:common` and the loader module then reports
`cannot find symbol` for it, Loom's cached remap of `:common` is stale.** Clear it once:

```bash
rm -rf common/build/devlibs common/build/loom-cache fabric/build/loom-cache .gradle/loom-cache
```

`.gradle/loom-cache/remapped_mods` holds the per-consumer remapped copy of `:common` that the loader
modules actually load; if a freshly added mixin class is present in the built jar but reported as
"not found" at runtime, this directory is the stale one. Do **not** reach for `./gradlew --stop`: it
stops every Gradle daemon on the machine, including ones serving unrelated projects. These cache
directories are per-project.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.21.5-3.4.3.3-fabric.jar
# -> neoforge/build/libs/parcool-1.21.5-3.4.3.3-neoforge.jar
```

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/neoforge.mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, and the `ServiceLoader` file that binds `ParCoolPlatform`.

Both jars need widened vanilla access to build ParCool's own zipline render pipelines, and neither
side can widen them on its own:

* `common/src/main/resources/parcool.accesswidener` (Fabric) and
  `neoforge/src/main/resources/META-INF/accesstransformer.cfg` (NeoForge, **mojmap** names) both
  expose `RenderType.create(String, int, RenderPipeline, CompositeState)`, the two
  `CompositeStateBuilder` setters, `RenderStateShard#NO_TEXTURE` / `#LIGHTMAP`, and
  `RenderPipelines#MATRICES_COLOR_FOG_SNIPPET` / `#PIPELINES_BY_LOCATION`.
* An access-transformer **method** entry needs the full JVM descriptor, return type included. Omitting
  it fails `createMinecraftArtifacts` with `Invalid method descriptor`, not with a compile error.
* `./gradlew :common:validateAccessWidener` (part of `:common:check`) checks the Fabric side.

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runclient    :neoforge:runserver
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin. A dedicated
server refuses to start until it is acknowledged, so before the first `:fabric:runServer` /
`:neoforge:runserver` create `<module>/run/eula.txt` containing:

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
`net.fabricmc.*` or `net.neoforged.*`.

## Status

Both distributables build, and both loaders have been run in a world: parkour, the zipline and the
custom rope render pipelines, the stamina HUD, the settings screen, rope dyeing, key rebinding and
progress surviving a world save/load all work, and both jars were installed into a real Prism
instance. Not covered: a dedicated NeoForge server, a cross-loader join (NeoForge client against
Fabric server) and two players in one world.
