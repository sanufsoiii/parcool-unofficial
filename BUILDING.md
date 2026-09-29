# Building ParCool (Architectury, Minecraft 1.21.7)

Requires JDK 21. The toolchain is declared through `java { toolchain { languageVersion = 21 } }` in
`build.gradle` and `org.gradle.jvmargs` in `gradle.properties`; there is no `org.gradle.java.home` and
no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`): Minecraft 1.21.7, Architectury API 18.0.8,
NeoForge 21.7.25-beta, Fabric Loader 0.16.14, Fabric API 0.128.2+1.21.7, Architectury Loom 1.17.493,
ModDevGradle 2.0.148, architectury-plugin 3.5.170, Gradle 9.4.1. Every one of those was resolved from
the publisher's `maven-metadata.xml`, and [NOTES.md §1](NOTES.md) records the endpoint for each.

The binding constraint is architectury-plugin 3.5.170: it is the newest published version and its
`common()`/`neoForge()` handling calls `LoomGradleExtension#disableObfuscation()`, which only exists
on the Loom 1.17 line, and Loom 1.17 in turn needs Gradle 9. There is no way to drop back to the
1.7.x Loom without dropping the plugin below 3.5.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.moddev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, and the Paraglider / EpicFight / BetterThirdPerson integrations. |

## Building

```bash
./gradlew :common:build   # on a fresh checkout
./gradlew build
```

**The two steps are not optional on a cold tree.** Architectury Loom resolves the `:common` project
dependency while it *configures* the loader modules, so the very first invocation has to be
`:common:build`; afterwards the root `build` task's `bootstrap` alias covers every case. This is
inherited from the 1.21.11 tree and is not a 1.21.7 bug — see NOTES.md §1.

**If you change `parcool.accesswidener`, clear the Loom caches once:**

```bash
rm -rf .gradle/loom-cache common/build
```

The access widener is not part of the Loom artifact cache key, so without this the previous
(un-widened) Minecraft jar is reused and the widening silently does not happen. The symptom is a
compile error about a `private`/`protected` member that the AW claims to be public.

`.gradle/loom-cache/remapped_mods` additionally holds the per-consumer remapped copy of `:common` that
the loader modules actually load; if a freshly added mixin class is present in the built jar but
reported as "not found" at runtime, that directory is the stale one.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.21.7-3.4.3.3-fabric.jar
# -> neoforge/build/libs/parcool-neoforge.jar
```

The Fabric jar contains the remapped `:common` code and assets plus the fabric module's own classes,
`fabric.mod.json`, the `parcool.accesswidener` (in `v2 intermediary`) and `parcool-common.mixins.json`,
and the `ServiceLoader` file that binds `ParCoolPlatform`. The `from { }` clauses in
`fabric/build.gradle`'s `distJar` are ordered deliberately: `duplicatesStrategy` is `EXCLUDE`, so the
first contributor wins, and the fabric module's copy of the access widener has to beat the common
module's.

The NeoForge jar contains the same code in mojmap plus the neoforge module's own classes,
`META-INF/neoforge.mods.toml`, `META-INF/accesstransformer.cfg`, `parcool-common.mixins.json` and the
`ServiceLoader` file.

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runclient    :neoforge:runserver
```

**These were not run for this port** — the brief forbade launching the game. They are wired and
documented; treat the in-game behaviour as unverified.

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin; that is how the mixin set would be verified. A dedicated server refuses to start until
it is acknowledged, so before the first `:fabric:runServer` / `:neoforge:runserver` create
`<module>/run/eula.txt` containing:

```
eula=true
```

`mixin.debug` can be switched for `mixin.debug.export` (writes `<module>/run/.mixin.out/`, one
transformed class per file) when a specific mixin needs inspecting.

### Booting straight into a world / a server

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

### The checklist a future revision should run

1. Both loaders boot with the mod listed and no failed mod state.
2. Enter a world. The ParCool attributes resolve on the first `Player#createAttributes` — that is the
   step that dies with `Registry is already frozen` if the NeoForge attribute split is wrong.
3. `grep "GL ERROR"` over the log is empty, and so is `grep "Invalid key"` (the GLFW keysym guard).
4. Every `key.parcool.*` entry in `assets/parcool/lang/en_us.json` is rebindable in Options →
   Controls, and pressing it drives its action.
5. A vanilla key ParCool also binds still works on **Fabric** — right-click places a block, Space
   jumps, Ctrl sprints. 1.21.7 is on the one-mapping-per-key side of `KeyMapping.MAP`, so
   `KeyBindings#restoreVanillaBindings` is load-bearing; if this fails, that repair is missing.
6. One action of each family: wall run, wall jump, slide, roll, dodge, vault, hide-in-block, zipline
   ride, stamina HUD, the settings screen.
7. Two clients on one server see each other's animations (`ActionStatePayload`).
8. `:common:checkCommonLoaderIndependence` passes.
9. The zipline rope is actually drawn — `RenderTypes.register()` is called from `Renderers.register()`
   precisely so the two pipelines exist before `ShaderManager` precompiles them during the resource
   reload; if the rope is missing or throws, that ordering is what broke.
10. The built jar boots in a real Prism instance. A jar that works in the dev environment and dies on a
    production client is a common failure mode: the Fabric access-widener namespace and the NeoForge
    mapping naming are both exactly this.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*`, `net.neoforged.*` or `net.minecraftforge.*`. It runs as part of `:common:check`, so
plain `./gradlew build` covers it.
