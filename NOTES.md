# NOTES — ParCool Architectury port to Minecraft 1.21.9

Working notes for this port. Findings, dead ends, decisions that the code cannot explain on its own,
and upstream bugs. Read-only reference trees are 1.21.11 (base) and 1.21.1 (second worked example).

---

## Phase 0 — orientation

Read `README.md` / `BUILDING.md` / `build.gradle` of both reference trees, plus their git history
(1.21.11 has three commits: import, packaging/move-tick/animator fixes, and the move-recursion guard
moved out of the mixin into a plain mod class).

Base tree layout copied verbatim: root + `common` (loom) + `fabric` (loom) + `neoforge` (ModDevGradle),
250 Java files, one `parcool-common.mixins.json`, one `parcool.accesswidener`, one
`META-INF/accesstransformer.cfg`.

**Known defect carried in from the base tree (per the handoff brief, to be re-checked here):**
`ConfigSpec#persist()` in 1.21.11 writes nothing — it only sets an unreadable `dirty` flag, so GUI
settings never survive a restart. The 1.21.1 tree has the working implementation. Same for
`BufferUtil`: 1.21.11 turned `ensureRoom` into a `static` over uninitialised state and dropped the
overflow checks from `putVector3i`/`putVec3`. Both must be taken from 1.21.1 here.

---

## Phase 1 — toolchain (resolved, not guessed)

Every number below was read out of a metadata endpoint on 2026-09-30.

| Property | Value | Where it came from |
|---|---|---|
| `minecraft_version` | `1.21.9` | target; present in `launchermeta.mojang.com/mc/game/version_manifest_v2.json` (released 2025-09-30, between 1.21.8 and 1.21.10) |
| `neo_version` | `21.9.16-beta` | last entry of the `21.9.*` line in `maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`; there is no non-beta 1.21.9 NeoForge build, every 1.21.9 build carries the `-beta` suffix |
| `loader_version` | `0.19.5` | `meta.fabricmc.net/v2/versions/loader/1.21.9` → `loader[0].loader.version` |
| `fabric_api_version` | `0.134.1+1.21.9` | highest `<version>*+1.21.9</version>` in `maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` (0.134.0 then 0.134.1, then the line moves to 1.21.10) |
| `architectury_api_version` | `19.0.1` | **see below** |
| `dev.architectury.loom` | `1.17.493` | highest published version in `maven.architectury.dev/dev/architectury/architectury-loom/maven-metadata.xml` |
| `architectury-plugin` | `3.5.170` | highest in `maven.architectury.dev/architectury-plugin/architectury-plugin.gradle.plugin/maven-metadata.xml` |
| `net.neoforged.moddev` | `2.0.148` | highest in `maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml` (2.0.147 → 2.0.148, then it stops) |
| Gradle wrapper | `9.4.1` | unchanged from the base tree; already cached in `~/.gradle/wrapper/dists` |

### Architectury API line for 1.21.9

`architectury-fabric`'s `fabric.mod.json` is the discriminator. Downloaded and read the
`depends.minecraft` out of every candidate jar in the 16.x–19.x range:

| architectury-fabric | `depends.minecraft` | usable on 1.21.9 |
|---|---|---|
| 16.0.3, 16.1.4 | `~1.21.4-` | no (1.21.4 line) |
| 17.0.3, 17.0.4 | `~1.21.6~` | no |
| 17.0.6 | `~1.21.6` | no |
| 17.0.8, 18.0.2 – 18.0.8 | `~1.21.7` | **yes** (`~1.21.7` = `>=1.21.7 <1.22.0`) |
| 19.0.1 | `~1.21.11` | no (`>=1.21.11`, and 1.21.9 < 1.21.11) |

So on the *nominal* Fabric dependency check the 18.x line is the one that accepts 1.21.9, and
**19.0.1 is not** — the handoff brief guessed that 19.0.x might be the 1.21.9 line, and it is not:
19.0.1 declares `~1.21.11`, i.e. it refuses 1.21.9 outright.

That still leaves 18.0.8, which was *compiled* against MC 1.21.7. Whether its own bytecode survives on
1.21.9 is a different question from whether its version predicate does, and it is not something the
predicate answers. Recorded as an open risk below.

NeoForge side: `architectury-neoforge` declares `minecraft [1.21.4,)` / `neoforge [21.0.110-beta,)` in
**both** 18.0.8 and 19.0.1, so the NeoForge metadata does not discriminate at all — the Fabric
`fabric.mod.json` is the only usable signal.

### Carried-over build facts from the already-finished ports

* `./gradlew build` in one invocation does **not** work on a clean checkout: Architectury Loom resolves
  the `:common` project dependency while it *configures* `:fabric`, so `:common`'s jar has to exist
  first. Working sequence: `./gradlew :common:build && ./gradlew build`. Inherited from 1.21.11 and
  documented in `BUILDING.md`; **not** "fixed", only documented.
* `minecraft-merged-*-sources.jar` in the Loom cache is generated *with the project's access widener
  already applied*, so it lies about member visibility. The truth is `javap` against the
  mojmap/named jar.
* ModDevGradle on Gradle 9 does not pick up `RepositoriesPlugin`, so Mojang's
  `libraries.minecraft.net` has to be listed explicitly in `neoforge/build.gradle`, otherwise
  `Could not find com.mojang:jtracy`. The base tree does **not** have that repository yet and will
  need it — see phase 1 findings.
* Loom 1.17.493 requires Gradle ≥ 9 (`architectury-plugin` 3.5.170 calls `disableObfuscation()`, which
  does not exist below Loom 1.17), so the wrapper stays on 9.4.1.
* `~/.gradle` is shared with the sibling 1.21.x ports, and `/tmp` is shared too — no snapshot files
  under fixed names in `/tmp`.
* `org.gradle.jvmargs=-Xmx2G` for this tree: three Gradle daemons run in parallel on this machine.
* `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on every Gradle invocation; the system `java` is 25 and
  Gradle 9 will not run on it.

---

## Phases 2–7

(filled in as the work proceeds)
