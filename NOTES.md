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

(see below, filled in as the port proceeds)

---

## 3. Bugs found

### Fixed

### Found, not fixed

---

## 4. What the next port should not trust

TBD
