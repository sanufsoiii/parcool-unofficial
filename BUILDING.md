# Building ParCool (Architectury, Minecraft 1.21.6)

Requires JDK 21. The toolchain is declared through `java { toolchain { languageVersion = 21 } }` in
`build.gradle` and `org.gradle.jvmargs` in `gradle.properties`; there is no `org.gradle.java.home`
and no machine-specific path anywhere in the build.

Versions (all in `gradle.properties` / `settings.gradle`; where each was resolved from is recorded in
`NOTES.md` §1): Minecraft 1.21.6, Architectury API 17.0.6, NeoForge 21.6.20-beta, Fabric Loader
0.16.10, Fabric API 0.128.2+1.21.6, Architectury Loom 1.17.493, ModDevGradle 2.0.90, Gradle 9.4.1.

| Module      | Toolchain | Contents |
|-------------|-----------|----------|
| `common`    | `dev.architectury.loom` | The whole mod, loader agnostic: registries (Architectury `DeferredRegister`), the `ParCoolData` player-data subsystem, the payload plumbing, the JSON `ConfigSpec`, the Architectury event bridge, the vanilla mixins and the `platform` seam. |
| `fabric`    | `dev.architectury.loom` | `ParCoolFabric` / `ParCoolFabricClient` entrypoints, `FabricParCoolPlatform`, `FabricParCoolNetwork`. |
| `neoforge`  | `net.neoforged.moddev` | `ParCoolNeoForge` / `ParCoolNeoForgeClient` entrypoints, `NeoForgeParCoolPlatform`, `NeoForgeParCoolNetwork`, and the Paraglider / EpicFight / BetterThirdPerson integrations. |

## Building

```bash
./gradlew :common:build   # required once on a fresh checkout
./gradlew build           # from then on
```

The two steps are needed because Architectury Loom resolves the `:common` jar while it *configures*
the loader modules, so `:common` has to have been built once. The root `build` task depends on a
`bootstrap` task (an alias of `:common:build`), which covers every case except a truly fresh checkout.
On a clean tree — `build/` and `.gradle/` deleted — run `:common:build` first.

**If you add a new class to `:common`, or edit an existing mixin, and the loader module then reports
`cannot find symbol` for it, or Loom prints**

```
Cannot remap <method> because it does not exist in any of the targets [net/minecraft/class_…]
```

**…Loom's cached remap of `:common` is stale.** Clear it and rebuild in this order:

```bash
rm -rf .gradle/loom-cache common/build/loom-cache common/build/devlibs \
       fabric/build/loom-cache neoforge/build/explodedCommon .architectury-transformer
./gradlew :common:build
./gradlew build
```

The order matters: `:fabric`'s configuration phase reads whatever `:common` jar exists at that
moment, so a single `./gradlew build` right after the wipe still reads the *old* one and prints the
`Cannot remap` line even though the source is already correct.

> Do **not** use `./gradlew --stop` here. Three ports build in parallel on this machine and the
> daemons are shared; stopping them kills the other two. Deleting the cache directories above is
> enough and is per-project.

## Distributables

```bash
./gradlew build
# -> fabric/build/libs/parcool-1.21.6-3.4.3.3-fabric.jar
# -> neoforge/build/libs/parcool-neoforge.jar
```

Each contains the `:common` code and assets plus the loader module's own classes, metadata
(`fabric.mod.json` / `META-INF/neoforge.mods.toml`), the shared `parcool.accesswidener` and
`parcool-common.mixins.json`, and the `ServiceLoader` file that binds `ParCoolPlatform`.

Check the two things that are easy to get wrong and that no compiler checks:

```bash
unzip -p fabric/build/libs/*-fabric.jar parcool.accesswidener | head -1
# must print: accessWidener<TAB>v2<TAB>intermediary
# `v2 named` makes Fabric Loader abort the boot before the window exists.

unzip -l neoforge/build/libs/parcool-neoforge.jar | grep -c 'class_'
# must be 0 - the NeoForge jar is mojmap-named on purpose
```

## Checks

```bash
./gradlew :common:checkCommonLoaderIndependence   # no net.fabricmc.* / net.neoforged.* in common/src/main
```

```bash
./gradlew :common:genSources
python3 tools/verify-mixin-targets.py \
    "$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.6-*/*.jar)" \
    common/src/main/java "$(command -v javap)"
# -> checked 29 mixin files, 0 problem(s)
```

`tools/verify-mixin-targets.py` exists because `parcool-common.mixins.json` sets
`"defaultRequire": 1`: a hook whose target no longer exists is a hard boot failure, and a hook whose
target still exists but means something else is invisible to both javac and Loom. The script
`javap`s the mojmap Minecraft jar (never the decompiled sources jar — Loom generates it with this
project's access widener already applied, so it lies about visibility) and checks every
`@Inject`/`@Redirect`/`@WrapWithCondition`/`@Accessor` target and `@Shadow` field by name *and*
descriptor.

## Running

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runClient    :neoforge:runserver
```

The dev runs pass `-Dmixin.debug=true -Dmixin.debug.verbose=true`, so the log lists every applied
ParCool mixin; that is how the mixin set can be checked without a manual client session. A dedicated
server refuses to start until it is acknowledged, so before the first `:fabric:runServer` /
`:neoforge:runserver` create `<module>/run/eula.txt` containing:

```
eula=true
```

Those `run/` directories are generated by the dev tasks and are not checked in.

`mixin.debug` can be switched for `mixin.debug.export` (writes `<module>/run/.mixin.out/`, one
transformed class per file) when a specific mixin needs inspecting.

### Booting straight into a world / a server

The cross-loader checks ("does a client of one loader actually join a server of the other one?",
"does the in-world render path survive a real frame?") are driven by the game's own quick-play flags,
so they can be repeated without a mouse:

```bash
# client of one loader -> dedicated server of the other one
./gradlew :neoforge:runserver                                   # waits for "Done (!)"
./gradlew :fabric:runClient -PparcoolQuickPlay=127.0.0.1:25566

# straight into a saved world, i.e. the render path with the mod's blocks in it
./gradlew :fabric:runClient   -PparcoolQuickPlayWorld="New World"
./gradlew :neoforge:runClient -PparcoolQuickPlayWorld="New World"
```

The dedicated servers need `online-mode=false` in `<module>/run*/server.properties` (offline dev
login) and a free `server-port`.

### What still has to be checked by hand

This port was validated statically only — the build, the access widener, the access transformer, the
mixin targets and both jar layouts. It has **not** been booted. The list below is what a real client
session still has to cover, and it is deliberately the same list PROMPT.md phase 6 gives:

- [ ] The game starts on **both** loaders, with the mod listed and no failed mod state.
- [ ] Enter a world. The ParCool attributes resolve on the first `Player#createAttributes` — that is
      the step that dies with `Registry is already frozen` if the NeoForge attribute split is wrong.
- [ ] `grep "GL ERROR" <log>` is empty. Also `grep "Invalid key"` — the GLFW keysym guard.
- [ ] Every key binding under `key.parcool.*` in `assets/parcool/lang/en_us.json` is rebindable in
      Options → Controls, and pressing it drives its action.
- [ ] A vanilla key that ParCool also binds still works (right-click places a block, Space jumps,
      Ctrl sprints) **on Fabric**. If it does not, the `KeyMapping.MAP` repair
      (`KeyBindings#restoreVanillaBindings`) is not doing its job — 1.21.6 has the single-mapping
      table, so the repair is required, not dead code.
- [ ] One action of each family: wall run, wall jump, slide, roll, dodge, vault, hide-in-block,
      zipline ride, stamina HUD, the settings screen.
- [ ] Both recipes that use the string ingredient form actually load (`/recipe give @s
      parcool:zipline_rope`); a bad ingredient form is a datapack error, not a crash.
- [ ] The camera roll in `CameraAnglesMixin` looks right — it is the one hook whose visual result is
      new code rather than a direct translation.
- [ ] Two clients on one server see each other's animations.
- [ ] The jar also loads in a real Prism instance, not only in dev. A jar that works in the dev
      environment and dies on a production client is a common failure mode: the Fabric
      access-widener namespace and the NeoForge mapping naming are both exactly this.
