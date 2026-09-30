#!/usr/bin/env bash
# Check the two distributables: class counts, mixin config completeness, the access
# widener namespace, the absence of a refmap, mixin target remapping (Fabric) vs mojmap
# (NeoForge), and the presence of every resource both jars must carry.
set -u
F=fabric/build/libs/parcool-1.21.8-3.4.3.3-fabric.jar
N=neoforge/build/libs/parcool-neoforge.jar
fail=0
say() { printf '%-64s %s\n' "$1" "$2"; }
chk() { if [ "$2" = "$3" ]; then say "$1" "OK ($2)"; else say "$1" "FAIL (got '$2', want '$3')"; fail=1; fi; }

common_classes=$(find common/src/main/java -name '*.java' | wc -l)
fabric_classes=$(find fabric/src/main/java -name '*.java' | wc -l)
neo_classes=$(find neoforge/src/main/java -name '*.java' | wc -l)

for J in "$F" "$N"; do
  echo "=================== $J"
  n=$(unzip -Z1 "$J" | grep -c '^com/alrex/parcool/.*\.class$')
  say "parcool classes" "$n (expect common $common_classes + loader)"
  for e in parcool-common.mixins.json parcool.accesswidener pack.mcmeta shouldersurfing_plugin.json LICENSE; do
    if unzip -Z1 "$J" | grep -qx "$e"; then say "  resource $e" "present"; else say "  resource $e" "MISSING"; fail=1; fi
  done
  if unzip -Z1 "$J" | grep -q 'refmap'; then say "  refmap" "PRESENT (unexpected)"; fail=1; else say "  refmap" "absent (as intended)"; fi
  # every mixin listed in the config must exist as a class
  missing=0
  while read -r m; do
    [ -z "$m" ] && continue
    unzip -Z1 "$J" | grep -qx "com/alrex/parcool/mixin/${m//./\/}.class" || { echo "    missing mixin class: $m"; missing=1; }
  done < <(python3 -c "
import json,sys
d=json.load(open('common/src/main/resources/parcool-common.mixins.json'))
for k in ('client','mixins','server'):
    for v in d.get(k,[]): print(v)
")
  chk "  all configured mixins present in the jar" "$missing" "0"
  # and the reverse: no mixin class in the jar that the config forgot
  orphans=0
  while read -r c; do
    cls=${c%.class}; cls=${cls//\//.}; cls=${cls#com.alrex.parcool.mixin.}
    grep -q "\"$cls\"" common/src/main/resources/parcool-common.mixins.json || { echo "    mixin class not in config: $cls"; orphans=1; }
  done < <(unzip -Z1 "$J" | grep '^com/alrex/parcool/mixin/.*\.class$')
  chk "  no orphan mixin classes" "$orphans" "0"
done

echo "=================== Fabric specifics"
chk "  accessWidener namespace" "$(unzip -p "$F" parcool.accesswidener | head -1 | tr '\t' ' ')" "accessWidener v2 intermediary"
say "  fabric.mod.json minecraft dep" "$(unzip -p "$F" fabric.mod.json | python3 -c 'import json,sys;print(json.load(sys.stdin)["depends"]["minecraft"])')"
say "  fabric.mod.json version" "$(unzip -p "$F" fabric.mod.json | python3 -c 'import json,sys;print(json.load(sys.stdin)["version"])')"
say "  mixin targets remapped to intermediary (Player#attack)" \
  "$(unzip -p "$F" com/alrex/parcool/mixin/common/PlayerMixin.class | strings | grep -c '^method_') objs"
say "  any net/minecraft named refs left in mixin annotations" \
  "$(unzip -p "$F" com/alrex/parcool/mixin/common/PlayerMixin.class | strings | grep -c 'net/minecraft')"
say "  ServiceLoader file" "$(unzip -p "$F" META-INF/services/com.alrex.parcool.platform.ParCoolPlatform)"

echo "=================== NeoForge specifics"
say "  accesstransformer.cfg" "$(unzip -Z1 "$N" | grep -c 'META-INF/accesstransformer.cfg') entry"
say "  mixin targets stay mojmap (Player#attack)" \
  "$(unzip -p "$N" com/alrex/parcool/mixin/common/PlayerMixin.class | strings | grep -c 'attack') hits"
say "  mojmap MC references in PlayerMixin" \
  "$(unzip -p "$N" com/alrex/parcool/mixin/common/PlayerMixin.class | strings | grep -c 'net/minecraft')"
say "  ServiceLoader file" "$(unzip -p "$N" META-INF/services/com.alrex.parcool.platform.ParCoolPlatform)"

echo
[ "$fail" = 0 ] && echo "ARTIFACT CHECKS PASSED" || echo "ARTIFACT CHECKS FAILED"
exit $fail
