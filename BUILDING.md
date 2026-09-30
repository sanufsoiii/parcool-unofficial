# Building ParCool (Architectury, Minecraft 1.21.8)

Requires JDK 21 or newer. The toolchain is declared through
`java { toolchain { languageVersion = 21 } }` in `build.gradle`, and `org.gradle.java.home` is not
set: there is no machine-specific path anywhere in the build. (If the system default `java` is newer
than 21, point `JAVA_HOME` at a 21 JDK for the Gradle invocation — Gradle 9 refuses to run on it
otherwise.)

Versions (all in `gradle.properties` / `settings.gradle`): Minecraft 1.21.8, Architectury API 17.0.8,
NeoForge 21.8.54, Fabric Loader 0.16.14, Fabric API 0.136.1+1.21.8, Architectury Loom 1.17.493,
ModDevGradle 2.0.148, Gradle 9.4.1. Where each number came from is written down in the comment blocks
of `gradle.properties` and `settings.gradle`.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.moddev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, `NeoForgeAttributes`, and the Paraglider / EpicFight / BetterThirdPerson / ShoulderSurfing integrations. |

## Building

```bash
./gradlew :common:build   # once on a fresh checkout
./gradlew build           # from then on
```

**One `./gradlew build` does not work on a clean checkout.** Architectury Loom resolves the `:common`
project dependency while it *configures* the loader modules, so `:common`'s jar has to exist before
they can be configured. The root `build` task depends on the `bootstrap` task (an alias of
`:common:build`), which covers every case except a truly fresh checkout. This is inherited from the
1.21.11 tree — document it, do not "fix" it.

**If you add a new class to `:common` and the loader module then reports
`cannot find symbol` for it, Loom's cached remap of `:common` is stale.** Clear it once:

```bash
rm -rf .gradle/loom-cache common/build fabric/build neoforge/build
```

`.gradle/loom-cache/remapped_mods` holds the per-consumer remapped copy of `:common` that the loader
modules actually load; if a freshly added mixin class is present in the built jar but reported as
"not found", this directory is the stale one. The same two directories go stale when
`parcool.accesswidener` changes, because the widener is not part of the artifact cache key — a wrong
descriptor in it is then silently ignored and the widening simply does not happen.

**Do not use `./gradlew --stop`** to flush this: it stops every Gradle daemon on the machine,
including ones serving unrelated projects. Delete the cache directories above instead — they are
per-project.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.21.8-3.4.3.3-fabric.jar
# -> neoforge/build/libs/parcool-neoforge.jar
```

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/neoforge.mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, `pack.mcmeta`, the `LICENSE`, and the `ServiceLoader` file that binds
`ParCoolPlatform`.

### Why the two jars are built differently

* **Fabric** — the distributable is assembled from `:common`'s `remapJar` output. That is Loom's own
  publish step: it applies the access widener, converts named → intermediary and, because Loom 1.17
  defaults to `mixinRemapType = static`, rewrites the mixin targets (both the annotation strings and
  the `@Shadow` descriptors) straight into the bytecode. There is deliberately **no refmap** — Loom
  1.17 does not run the legacy mixin annotation processor, and fabric-api, built with the same Loom,
  ships none either and advertises `Fabric-Loom-Mixin-Remap-Type: static` in its manifest. The AW has
  to be in `v2 intermediary` in the distributed jar (`v2 named` makes Fabric Loader abort the boot
  before the window exists), which is why `:fabric:distJar` takes the fabric module's remapped copy
  and drops `:common`'s. The order of the `from { }` clauses matters: `duplicatesStrategy` is
  `EXCLUDE`, so the first contributor wins, and the fabric module's own remapped classes have to come
  first.
* **NeoForge** — mojmap-named bytecode, because that is what the NeoForge production runtime loads
  (verified against a shipped mod of this era, see the header of `neoforge/build.gradle`).
  architectury-plugin's `neoForge()` transform is **not** used: it needs a Loom-based NeoForge setup
  that cannot merge the Mojang and NeoForge mappings, so `:neoforge` assembles the distributable
  explicitly and consumes `:common`'s `transformProductionNeoForge` artifact as a file dependency.
  The mojang repositories are declared explicitly in `neoforge/build.gradle` because ModDevGradle does
  not add `RepositoriesPlugin` itself.

## Verification

The build succeeding says nothing about the artifact; these check the artifact itself.

```bash
bash tools/verify_artifacts.sh   # both jars: classes, mixin config, AW namespace, no refmap
```

`verify_artifacts.sh` checks each distributable end to end: the class list, that every mixin named in
`parcool-common.mixins.json` is present *and* that no mixin class in the jar is missing from the
config, the resources both jars must carry, the access-widener namespace, and the absence of a refmap.

`tools/AtCheck.java` parses `neoforge/src/main/resources/META-INF/accesstransformer.cfg` with
NeoForge's own `AccessTransformerList`, which is the only way to find out that an AT entry is
syntactically wrong before the game refuses to start. Compile it against
`net.neoforged:accesstransformers` plus `org.antlr:antlr4-runtime` and run it with the AT path.

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer      :fabric:runClient2
./gradlew :neoforge:runclient    :neoforge:runserver    :neoforge:runClient2
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin. A dedicated
server refuses to start until it is acknowledged, so before the first `:fabric:runServer` /
`:neoforge:runserver` create `<module>/run*/eula.txt` containing:

```
eula=true
```

Those `run*/` directories are generated by the dev tasks and are not checked in.

`mixin.debug` can be switched for `mixin.debug.export` (writes `<module>/run*/.mixin.out/`, one
transformed class per file) when a specific mixin needs inspecting.

### Booting straight into a world / a server

The two cross-loader checks ("does a client of one loader actually join a server of the other one?",
"does the in-world render path survive a real frame?") are driven by the game's own quick-play flags,
so they can be repeated without a mouse:

```bash
./gradlew :neoforge:runServer                                   # waits for "Done (!)"
./gradlew :fabric:runClient -PparcoolQuickPlay=127.0.0.1:25566

./gradlew :fabric:runClient  -PparcoolQuickPlayWorld="New World"
./gradlew :neoforge:runClient -PparcoolQuickPlayWorld="New World"
```

The dedicated servers need `online-mode=false` in `<module>/run*/server.properties` (offline dev
login) and a free `server-port`.

## Loader independence check

`./gradlew :common:checkCommonLoaderIndependence` fails the build if `common/src/main` ever imports
`net.fabricmc.*` or `net.neoforged.*`. It is wired into `:common:check`, so a plain `:common:build`
runs it.
