#!/usr/bin/env python3
"""
Checks every @Mixin target and every @Inject/@Redirect/@Modify*/@Wrap*/@Accessor/@Invoker/@Shadow
target under common/src/main/java against the resolved 1.21.10 mojmap jar.

The point of this script is that it must be *able* to fail. A previous port's checker parsed
annotations with a regex like `\\(([^)]*)\\)`, which silently truncated on the first `)` inside a
method descriptor - i.e. it skipped every mixin target that had one - and then reported "0 problems"
about an entirely broken tree. So:

  * the descriptor parser balances parentheses instead of cutting at the first `)`;
  * the method-name extraction takes the identifier immediately before the argument list, not
    "the last whitespace-separated token" (which breaks on a generic signature like
    `extractRenderState(AvatarlikeEntity, Foo, float)`);
  * `self_test()` runs the very same code path against a wrong-arity target, a wrong-name target
    and a genuinely correct target, and refuses to report anything unless all three behave.
"""

import os
import re
import subprocess
import sys

JAR = None

_ENTRIES = set()
_MEMBERS = {}
_FIELDS = {}
_SUPERS = {}


# ------------------------------------------------------------------ jar / javap plumbing

def _load_entries():
    global _ENTRIES
    if _ENTRIES:
        return
    out = subprocess.run(["unzip", "-Z1", JAR], capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        if line.endswith(".class"):
            _ENTRIES.add(line)


def class_exists(fqn):
    _load_entries()
    f = fqn.replace(".", "/")
    return f + ".class" in _ENTRIES or f.replace("$", "/") + ".class" in _ENTRIES


def javap(fqn):
    """(ok, [(methodName, descriptor), ...]) for every method javap -p -s reports on fqn."""
    if fqn in _MEMBERS:
        return _MEMBERS[fqn]
    proc = subprocess.run(
        ["/usr/lib/jvm/java-21-openjdk/bin/javap", "-cp", JAR, "-p", "-s", fqn],
        capture_output=True, text=True)
    if proc.returncode != 0:
        _MEMBERS[fqn] = (False, [])
        return _MEMBERS[fqn]
    members = []
    pending = None
    for line in proc.stdout.splitlines():
        s = line.strip()
        if s.startswith("descriptor:"):
            if pending is not None:
                members.append((pending, s.split(":", 1)[1].strip()))
            pending = None
        elif s.endswith(";") and "(" in s and ")" in s:
            m = re.search(r"([A-Za-z_$][\w$]*)\s*\(", s)
            pending = m.group(1) if m else None
    _MEMBERS[fqn] = (True, members)
    return _MEMBERS[fqn]


def has_method(fqn, name, descriptor):
    ok, members = javap(fqn)
    if not ok:
        return False
    if name is None or name == "<any>":
        return any(d == descriptor for _, d in members)
    return any(n == name and d == descriptor for n, d in members)


def has_method_by_name(fqn, name):
    ok, members = javap(fqn)
    return ok and any(n == name for n, _ in members)


def has_field(fqn, name):
    if fqn in _FIELDS:
        return name in _FIELDS[fqn]
    proc = subprocess.run(["/usr/lib/jvm/java-21-openjdk/bin/javap", "-cp", JAR, "-p", fqn],
                          capture_output=True, text=True)
    names = set()
    if proc.returncode == 0:
        for line in proc.stdout.splitlines():
            s = line.strip().rstrip(";")
            if "(" in s or s.startswith(("class ", "interface ", "Compiled from")):
                continue
            m = re.match(r"^(?:public |private |protected |static |final |transient |volatile )+"
                         r"[\w.$<>\[\], ?]+\s+(\w+)$", s)
            if m:
                names.add(m.group(1))
    _FIELDS[fqn] = names
    return name in names


def supers(fqn, seen=None):
    if seen is None:
        seen = frozenset()
    if fqn in seen:
        return []
    if fqn in _SUPERS:
        return _SUPERS[fqn]
    seen = seen | {fqn}
    proc = subprocess.run(["/usr/lib/jvm/java-21-openjdk/bin/javap", "-cp", JAR, "-p", fqn],
                          capture_output=True, text=True)
    direct = []
    if proc.returncode == 0:
        lines = proc.stdout.splitlines()
        header = lines[1] if len(lines) > 1 else ""
        for sup in re.findall(r"(?:extends|implements)\s+([\w.$]+)", header):
            sup = sup.replace("<>", "")
            if class_exists(sup):
                direct.append(sup)
    chain = []
    for sup in direct:
        if sup not in chain:
            chain.append(sup)
        for anc in supers(sup, seen):
            if anc not in chain:
                chain.append(anc)
    _SUPERS[fqn] = chain
    return chain



_FIELDDESC = {}


def _has_field_descriptor(fqn, name, descriptor):
    key = fqn
    if key not in _FIELDDESC:
        proc = subprocess.run(["/usr/lib/jvm/java-21-openjdk/bin/javap", "-cp", JAR, "-p", "-s", fqn],
                              capture_output=True, text=True)
        found = set()
        if proc.returncode == 0:
            pending = None
            for line in proc.stdout.splitlines():
                s = line.strip()
                if s.startswith("descriptor:"):
                    if pending:
                        found.add((pending, s.split(":", 1)[1].strip()))
                    pending = None
                elif s.endswith(";") and "(" not in s and ")" not in s:
                    m = re.search(r"([A-Za-z_$][\w$]*)$", s)
                    pending = m.group(1) if m else None
        _FIELDDESC[key] = found
    return (name, descriptor) in _FIELDDESC[key]


# ------------------------------------------------------------------ annotation parsing

def balanced_end(text, start):
    """text[start] == '('; return the index just past the matching ')'."""
    depth = 0
    in_str = False
    esc = False
    i = start
    while i < len(text):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return i + 1
        i += 1
    raise ValueError("unbalanced parentheses at %d" % start)


def string_literals(chunk):
    out = []
    i = 0
    while i < len(chunk):
        if chunk[i] == '"':
            j = i + 1
            buf = []
            while j < len(chunk):
                if chunk[j] == "\\":
                    buf.append(chunk[j + 1])
                    j += 2
                    continue
                if chunk[j] == '"':
                    break
                buf.append(chunk[j])
                j += 1
            out.append("".join(buf))
            i = j + 1
        else:
            i += 1
    return out


def annotations(text):
    for m in re.finditer(r"@(\w+)\s*\(", text):
        end = balanced_end(text, m.end() - 1)
        yield m.group(1), string_literals(text[m.end():end - 1])


INJECTORS = ("Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyConstant", "ModifyVariable",
             "ModifyExpressionValue", "ModifyReceiver", "ModifyReturnValue", "WrapWithCondition",
             "WrapOperation")


def resolve(name, imports):
    """Resolve `Outer.Inner` / `Simple` against the file's imports."""
    if name.startswith(("net.minecraft", "com.mojang", "org.joml", "org.spongepowered", "tictim.")):
        return name
    parts = name.split(".")
    head = imports.get(parts[0])
    if head is None:
        return name
    if len(parts) == 1:
        return head
    return head + "$" + "$".join(parts[1:])


QUALIFIED = re.compile(r"^(?:L)?([\w/$]+);([A-Za-z_$][\w$]*)(\([^)]*\).*)$")


def _split_target(spec):
    """Mixin accepts four target spellings:
         name                        -> ('name', None)
         name(desc)                  -> ('name', '(desc)')
         (desc)                      -> (None,    '(desc)')
         Lowner;name(desc)           -> ('name',  '(desc)')   with the owner checked separately
    """
    if spec.startswith("(") and spec.endswith(")"):
        return None, spec
    m = QUALIFIED.match(spec)
    if m:
        return m.group(2), m.group(3)
    i = spec.find("(")
    if i < 0:
        return spec, None
    return spec[:i], spec[i:]


problems = []
checked = 0


def check_file(path, report):
    global checked
    src = open(path, encoding="utf-8").read()
    imports = {}
    for m in re.finditer(r"^import\s+(?:static\s+)?([\w.$]+)\s*;", src, re.M):
        imports[m.group(1).rsplit(".", 1)[-1]] = m.group(1)

    targets = [resolve(t, imports)
               for t in re.findall(r"@Mixin\(\s*([\w.$]+)\s*\.\s*class\s*\)", src)]
    if not targets:
        return
    if len(targets) > 1:
        report("%s: %d @Mixin targets, expected 1" % (path, len(targets)))
    for t in targets:
        if not class_exists(t):
            report("%s: @Mixin target class does not exist in 1.21.10: %s" % (path, t))
    own = targets[0]
    if not class_exists(own):
        return

    for ann, args in annotations(src):
        if ann not in INJECTORS:
            continue
        if not args:
            report("%s: @%s has no target" % (path, ann))
            continue
        name, desc = _split_target(args[0])
        checked += 1
        if desc is None:
            if not has_method_by_name(own, name):
                report('%s: @%s("%s") - no method of that name on %s or its supertypes'
                       % (path, ann, name, own))
            continue
        ok, _ = javap(own)
        if not ok:
            continue
        if has_method(own, name, desc):
            continue
        if any(has_method(sup, name, desc) for sup in supers(own)):
            # inherited: the descriptor's declared owner has to exist for the mixin to resolve it
            for t in re.findall(r"L([\w/$]+);", desc):
                if not class_exists(t.replace("/", ".")):
                    report("%s: @%s target descriptor names a missing type %s" % (path, ann, t))
            continue
        report('%s: @%s("%s") - no method "%s" with that descriptor on %s or its supertypes'
               % (path, ann, args[0], name, own))

    # @At(value = "INVOKE"|"INVOKESTATIC"|..., target = "Lowner;name(desc)RET") - the target must
    # name a method that exists, otherwise the injection silently matches nothing.
    for m in re.finditer(r'target\s*=\s*"(L[^"]+)"', src):
        spec = m.group(1)
        q = QUALIFIED.match(spec)
        if not q:
            problems.append("%s: @At target is not a method reference: %s" % (path, spec))
            continue
        checked += 1
        owner = q.group(1).replace("/", ".")
        name, desc = q.group(2), q.group(3)
        if not class_exists(owner):
            problems.append("%s: @At target names a missing class %s" % (path, owner))
        elif not has_method(owner, name, desc) and not any(
                has_method(sup, name, desc) for sup in supers(owner)):
            # The owner in an @At(target=...) is the *constant-pool* owner of the invoke, which for
            # an inherited call is the receiver's static type, not the declaring class - e.g.
            # Player.setSprinting is really Entity#setSprinting. So a supertype counts as a hit.
            problems.append('%s: @At target "%s" - no method "%s" with that descriptor on %s '
                            'or its supertypes' % (path, spec, name, owner))

    for m in re.finditer(r'@At\(\s*(?:value\s*=\s*)?"(FIELD|GETFIELD|SETFIELD)[^"]*"\s*,?[^)]*'
                         r'target\s*=\s*"L([^;]+);(\w+):([^"]+)"', src):
        checked += 1
        owner, fname, fdesc = m.group(2).replace("/", "."), m.group(3), m.group(4)
        if not class_exists(owner):
            problems.append("%s: @At FIELD target names a missing class %s" % (path, owner))
        elif not _has_field_descriptor(owner, fname, fdesc):
            problems.append('%s: @At FIELD target "%s" - no field "%s:%s" on %s'
                            % (path, m.group(0), fname, fdesc, owner))

    for m in re.finditer(r"@(Accessor|Invoker)\(\s*\"([^\"]+)\"\s*\)", src):
        checked += 1
        if not (has_method_by_name(own, m.group(2)) or has_field(own, m.group(2))):
            report('%s: @%s("%s") - no such member on %s' % (path, m.group(1), m.group(2), own))

    for m in re.finditer(r"@Shadow\b(.*?)(?:;|\n\n)", src, re.S):
        body = m.group(1)
        n = re.search(r"([A-Za-z_$][\w$]*)\s*(?:=[^;]*)?$", body.strip())
        if not n:
            continue
        checked += 1
        if not (has_field(own, n.group(1)) or has_method_by_name(own, n.group(1))):
            report('%s: @Shadow "%s" does not exist on %s' % (path, n.group(1), own))


# ------------------------------------------------------------------ self test

BROKEN_ARITY = '''
package com.alrex.parcool.selfcheck;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerModel.class)
public abstract class BrokenArity {
    // right name, WRONG arity: 1.21.10's setupAnim(AvatarRenderState) takes one argument
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("HEAD"), cancellable = true)
    protected void broken(AvatarRenderState s, float extra, CallbackInfo ci) {}
}
'''

BROKEN_NAME = BROKEN_ARITY.replace("BrokenArity", "BrokenName") \
    .replace('method = "setupAnim(', 'method = "setupAnimNoSuchThing(')

REAL_TARGET = BROKEN_ARITY.replace("BrokenArity", "RealTarget") \
    .replace('"setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V"',
             '"setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"') \
    .replace("AvatarRenderState s, float extra,", "AvatarRenderState s,")


def self_test():
    d = "/tmp/parcool-mixin-selftest/com/alrex/parcool/selfcheck"
    os.makedirs(d, exist_ok=True)
    global checked
    saved = list(problems)
    checked = 0

    def run(name, source):
        del problems[:]
        p = os.path.join(d, name + ".java")
        open(p, "w", encoding="utf-8").write(source)
        check_file(p, lambda m: problems.append(m))

    run("BrokenArity", BROKEN_ARITY)
    arity_reported = any("no method" in x for x in problems)

    run("BrokenName", BROKEN_NAME)
    name_reported = any("no method" in x for x in problems)

    run("RealTarget", REAL_TARGET)
    real_silent = not problems

    deep = string_literals(
        '"setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"') == \
        ["setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"]

    del problems[:]
    problems.extend(saved)

    ok = arity_reported and name_reported and real_silent and deep
    print("self-test: wrong arity reported=%s  wrong name reported=%s  real target silent=%s  "
          "nested parens parsed=%s  -> %s"
          % (arity_reported, name_reported, real_silent, deep, "PASS" if ok else "FAIL"))
    if not ok:
        print("self-test problems: %s" % problems)
        print("the checker is not trustworthy, refusing to report anything", file=sys.stderr)
        sys.exit(2)


def main():
    global JAR
    if len(sys.argv) < 3:
        print("usage: verify_mixins.py <1.21.10-mojmap.jar> <source-root>...")
        sys.exit(1)
    JAR = sys.argv[1]

    self_test()

    n = 0
    for root in sys.argv[2:]:
        for dirpath, _, files in os.walk(root):
            for fn in sorted(files):
                if fn.endswith(".java"):
                    check_file(os.path.join(dirpath, fn), problems.append)
                    n += 1

    print("checked %d java files, %d injection/field targets" % (n, checked))
    if problems:
        print("\n%d PROBLEM(S):" % len(problems))
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print("no problems found")


if __name__ == "__main__":
    main()
