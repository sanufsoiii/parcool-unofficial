# NOTES — ParCool! 3.4.3.3 → Minecraft 1.21.4 (Architectury API)

Working notes for this port. Everything that had to be decided by looking at real data rather than
by memory is recorded here, together with the dead ends and the traps for the next port.

## 0. Layout / references

* Base (imported, then retargeted): `parcool-Architectury-API-1.21.11` — read-only.
* Worked example of a finished port: `parcool-Architectury-API-1.21.1` — read-only.
* This tree: `parcool-Architectury-API-1.21.4`.

Neither reference tree was modified. Both were read with `git`/`diff` only.

## 1. Resolved toolchain — where each number came from

| Property | Value | Source of the number |
|---|---|---|
| `minecraft_version` | `1.21.4` | the task itself |
| `neo_version` | `21.4.158` | `maven-metadata.xml` of `net.neoforged:neoforge`; 21.4.x has 158 releases, the newest non-beta is the last 21.4 build |
| `architectury_api_version` | `16.1.4` | `architectury-fabric-16.1.4.jar` → `fabric.mod.json` `depends.minecraft = "~1.21.4-"`; `architectury-neoforge-16.1.4.jar` → `neoforge.mods.toml` `minecraft [1.21.4,)` / `neoforge [21.0.110-beta,)`. 15.0.3 declares the same MC line, 16.1.4 is the newer sibling; 17.x already moved to `~1.21.7` |
| `loader_version` | `0.16.14` | `https://meta.fabricmc.net/v2/versions/loader/1.21.4` — 0.16.14 is the newest 0.16 line build, i.e. the one contemporary with 1.21.4. architectury-fabric 16.1.4 asks for `fabricloader >= 0.15.11` |
| `fabric_api_version` | `0.119.4+1.21.4` | Modrinth API `fabric-api` filtered on game version 1.21.4 — newest published |
| `dev.architectury.loom` | `1.7.435` | proven by the 1.21.1 port on the identical project layout; verified to configure MC 1.21.4 (`./gradlew :common:tasks` succeeds) |
| `architectury-plugin` | `3.5.170` | same as both reference ports |
| `net.neoforged.moddev` | `1.0.9` | the 1.0.x line is the NeoForge 21.1–21.4 line; 2.x is 21.5+. Same version the 1.21.1 port uses with `neoForge { version = … }` |
| Gradle wrapper | `8.10.2` | same as the 1.21.1 port; Gradle 9.x is not an option on Loom 1.7 |
| Java toolchain | 21 | both reference ports; `java { toolchain { languageVersion = 21 } }` |
| `org.gradle.jvmargs` | `-Xmx2G` | **changed from the reference ports' `-Xmx3G`**: three ports build side by side on a 31 GB box and three 3 GB daemons plus their workers do not fit. Do not "restore" this without checking how many daemons are running. |

### Launcher JDK

The machine's default `java` is **25**, which Gradle 8.10.2 does not support (max 23). Every Gradle
invocation in this port therefore ran with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. This is an
environment detail, not a build setting: there is deliberately **no** `org.gradle.java.home` in
`gradle.properties` (machine-specific path, forbidden by the definition of done). The build itself
only asks for toolchain 21.

## 2. How to resolve "which side of the delta am I on"

`./gradlew :common:genSources` in this project produces Vineflower-decompiled mojmap sources for
1.21.4 under the Loom cache. Anything that is not decidable from the source of the mod itself is
decided from those. Where a decompile was not needed, the decision is recorded here with the reason.

## 3. Version deltas — decisions

(filled in as the port proceeds; see the per-delta entries below)

## 4. Upstream / reference-port bugs found

(filled in as the port proceeds)

## 5. What the next port must not trust

(filled in as the port proceeds)
