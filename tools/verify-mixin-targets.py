#!/usr/bin/env python3
"""Re-derive every ParCool mixin target against the real Minecraft jar.

Why this exists
---------------
`common/src/main/resources/parcool-common.mixins.json` sets `"defaultRequire": 1`, so a
`@Inject(method = "...")` whose target does not exist is a hard boot failure - but the opposite case,
a name that still exists with a *different* meaning, is invisible. javac happily accepts a mixin
target that does not resolve, and Loom's static mixin remap only warns.

This script closes that gap without launching the game: it reads every mixin source, resolves
`@Mixin(X.class)` through the file's imports, then `javap`s X (and its superclasses) from the mojmap
Minecraft jar and asserts that every `@Inject` / `@Redirect` / `@WrapWithCondition` / `@Accessor`
method name or descriptor and every `@Shadow` field is really there.

It deliberately uses `javap` on the *named* jar and not the decompiled sources jar: Loom generates
`minecraft-merged-*-sources.jar` with the project's access widener already applied, so it reports
widened members as public and would make a broken access widener look correct.

Usage
-----
    ./gradlew :common:genSources          # populates the Loom cache
    python3 tools/verify-mixin-targets.py <path-to-mojmap-minecraft-jar> common/src/main/java

The mojmap jar is the one under
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/<version>-.../`.

Exit code is 0 when nothing is missing.
"""

import glob
import os
import re
import subprocess
import sys

ANNOTATIONS = (
    "Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant",
    "WrapOperation", "WrapWithCondition", "ModifyExpressionValue", "Overwrite", "Accessor", "Invoker",
)

_cache = {}


def _javap(cls, *flags):
    key = (cls, flags)
    if key not in _cache:
        proc = subprocess.run(
            [sys.argv[3] if len(sys.argv) > 3 else "javap", *flags, "-cp", MCJ, cls],
            capture_output=True, text=True,
        )
        _cache[key] = proc
    return _cache[key]


def declared(cls):
    """[(signature, descriptor)] of `cls`, or None when the class does not exist."""
    proc = _javap(cls, "-p", "-s")
    out = []
    lines = proc.stdout.splitlines()
    for i, line in enumerate(lines):
        if i + 1 < len(lines) and "descriptor:" in lines[i + 1]:
            out.append((line.strip().rstrip(";"), lines[i + 1].split("descriptor:")[1].strip()))
    return out or None


def hierarchy(cls, depth=0):
    """Every member of `cls` and its supertypes, plus the superclass chain."""
    if depth > 6:
        return []
    own = declared(cls)
    if own is None:
        return []
    members = list(own)
    proc = _javap(cls, "-p")
    m = re.search(r"^\s*(?:public |protected |private |abstract |final |static )*"
                  r"(?:class|interface|enum|record)\s+[\w.$]+(?:<[^\n]*?>)?\s+"
                  r"(?:extends\s+([\w.$<>]+))?", proc.stdout, re.M)
    if m and m.group(1):
        members += hierarchy(m.group(1).split("<")[0], depth + 1)
    return members


def resolve_target(source, simple):
    """`@Mixin(Outer.Inner.class)` -> the fully qualified name, via the file's own imports."""
    outer = simple.split(".")[0]
    m = re.search(r"^import\s+([\w.]*\." + re.escape(outer) + r");", source, re.M)
    return m.group(1) + simple[len(outer):] if m else simple


def annotation_arguments(source, names):
    """[(annotation, argument text)] with *balanced* parentheses.

    A plain "annotation followed by (up to the first close paren)" is not good enough: a target is
    with a descriptor, i.e. `@Inject(method = "setupAnim(L.../PlayerRenderState;)V", at = @At("HEAD"))`,
    and `[^)]*` stops at the `)` inside the string literal. The annotation then looks argument-less,
    the `method = "..."` search finds no closing quote, and the hook is silently *not* checked - which
    is the exact failure this script exists to catch.
    """
    pattern = re.compile(r"@(" + "|".join(names) + r")\b")
    for m in pattern.finditer(source):
        i = m.end()
        while i < len(source) and source[i].isspace():
            i += 1
        if i >= len(source) or source[i] != "(":
            yield m.group(1), ""
            continue
        depth, start = 0, i
        while i < len(source):
            if source[i] == "(":
                depth += 1
            elif source[i] == ")":
                depth -= 1
                if depth == 0:
                    i += 1
                    break
            i += 1
        yield m.group(1), source[start + 1:i - 1]


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    minecraft_jar, root = sys.argv[1], sys.argv[2]
    global MCJ
    MCJ = minecraft_jar

    files = sorted(glob.glob(os.path.join(root, "**", "mixin", "**", "*.java"), recursive=True))
    problems = []

    for path in files:
        source = open(path, encoding="utf-8").read()
        m = re.search(r"@Mixin\(\s*([\w.]+)\.class\s*\)", source)
        if not m:
            problems.append((path, "no @Mixin target"))
            continue
        target = resolve_target(source, m.group(1))
        members = hierarchy(target)
        if not members:
            problems.append((path, f"target class {target} does not exist in this Minecraft"))
            continue

        # @Shadow fields. `@Shadow <modifiers> <type> <name>;` on one or two lines - a shadow may
        # carry its own annotations (@Final, @Nullable), and a mixin may also declare several on the
        # same line, so the pattern only has to find `name;` preceded by a type token.
        for sm in re.finditer(
                r"@Shadow(?:\s*@\w+)*\s+(?:public\s+|protected\s+|private\s+)?(?:static\s+)?"
                r"(?:final\s+)?[\w.$<>,\[\]?]+\s+(\w+)\s*;", source):
            name = sm.group(1)
            if not any(sig.split()[-1].rstrip(";") == name for sig, _ in members):
                problems.append((path, f"@Shadow field {name} is not declared on {target}"))

        for kind, args in annotation_arguments(source, ANNOTATIONS):
            ann = type("M", (), {"group": staticmethod(lambda n, k=kind: k if n == 1 else args)})
            mm = re.search(r'method\s*=\s*(?:\{)?\s*"([^"]*)"', args)
            if not mm:
                continue
            for wanted in [t.strip() for t in mm.group(1).split(",")]:
                # Mixin accepts three spellings: a bare name, `name(args)ret`, and the fully
                # qualified `Lowner;name(args)ret`. Strip the owner prefix of the third form first;
                # its return type may be omitted, in which case only the name and the arguments are
                # checked.
                if wanted.startswith("L") and ";" in wanted:
                    # split on the FIRST semicolon: the owner part has none, and the descriptor
                    # after it may contain several more.
                    wanted = wanted.split(";", 1)[1]
                dm = re.match(r"^(.+?)\((.*?)\)(.*)$", wanted)
                if dm:
                    # The pair matters, not the descriptor alone: `hurtOrSimulate(DamageSource,F)` and
                    # a hypothetical `hurt(DamageSource,F)Z` share one descriptor, so checking the
                    # descriptor on its own would pass a hook that points at the wrong method.
                    name, ret = dm.group(1), dm.group(3)
                    desc = "(" + dm.group(2) + ")" + ret
                    hit = any(sig.split("(")[0].split()[-1] == name and d == desc
                              for sig, d in members)
                    if not hit and not ret:
                        # Return type omitted: the arguments still have to match.
                        hit = any(sig.split("(")[0].split()[-1] == name
                                  and d == "(" + dm.group(2) + ")" or
                                  (sig.split("(")[0].split()[-1] == name
                                   and d.startswith("(" + dm.group(2) + ")"))
                                  for sig, d in members)
                    if not hit:
                        problems.append((path, f'@{ann.group(1)} "{wanted}" not found on {target}'))
                else:
                    if not any(sig.split("(")[0].split()[-1] == wanted for sig, _ in members):
                        problems.append((path, f'@{ann.group(1)} "{wanted}" not found on {target}'))

    for path, why in problems:
        print("PROBLEM", os.path.relpath(path, root), "::", why)
    print(f"checked {len(files)} mixin files, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
