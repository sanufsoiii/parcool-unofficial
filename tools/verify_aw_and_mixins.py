#!/usr/bin/env python3
"""Verify ParCool's access widener and mixin set against the *target* Minecraft jars.

Why this exists: Loom's class tweaker applies an access widener entry by exact class + member
name + descriptor and silently ignores a mismatch, so a green build proves nothing about the
widening; and a mixin `@Inject(method = "...")` that stops matching is a silent runtime no-op
(with `defaultRequire: 1` it is a hard boot failure instead). Both are checked here against
the real jars:

  * RAW  = the mojmap ("named") jar, untouched                       -> ground truth
  * AWED = the same jar with ParCool's access widener applied by Loom -> what javac sees

Members are looked up through the whole superclass chain (a mixin may inject a method it only
inherits - `KeyboardInput#tick()` lives on `Input`), constant-pool parsing is used to resolve
the chain, and nested classes are matched as `Outer$Inner`.

Self-test (`--self-test`): the checker is run with two deliberately broken inputs - the AW entry
`RenderPipelines#PIPELINES_BY_LOCATION` renamed to `..._X`, and every mixin `method =` target
appended with `X`. Both kinds of fault must be reported; a verifier that reports neither is
itself broken and its real output must not be trusted.
"""
import os
import re
import struct
import subprocess
import sys
import zipfile
from collections import defaultdict

RAW = open('/tmp/opencode/mc_raw.txt').read().strip()
AWED = open('/tmp/opencode/mc_aw.txt').read().strip()
AW_FILE = 'common/src/main/resources/parcool.accesswidener'
MIXIN_DIR = 'common/src/main/java/com/alrex/parcool/mixin'

ANNOTATIONS = ('Inject', 'Redirect', 'ModifyArg', 'ModifyArgs', 'ModifyConstant',
               'ModifyVariable', 'WrapWithCondition', 'WrapOperation',
               'ModifyExpressionValue', 'ModifyReturnValue')

ZIPS = {RAW: zipfile.ZipFile(RAW), AWED: zipfile.ZipFile(AWED)}
_hier_cache, _mem_cache = {}, {}

# javap dominates the runtime, so its raw output is memoised on disk inside this project's own
# (git-ignored) build directory rather than in /tmp, which is shared with the other ports.
import json
_CACHE_PATH = os.path.join('.gradle', 'verify_aw_and_mixins.cache.json')
try:
    with open(_CACHE_PATH, encoding='utf-8') as fh:
        _JAVAP_CACHE = json.load(fh)
except (OSError, ValueError):
    _JAVAP_CACHE = {}


def _save_cache():
    try:
        os.makedirs(os.path.dirname(_CACHE_PATH), exist_ok=True)
        with open(_CACHE_PATH, 'w', encoding='utf-8') as fh:
            json.dump(_JAVAP_CACHE, fh)
    except OSError:
        pass


# --------------------------------------------------------------------------- class file
def class_info(jar, cls):
    """{'super': str|None, 'interfaces': [str]} for a binary class name in the jar."""
    key = (jar, cls)
    if key in _hier_cache:
        return _hier_cache[key]
    info = None
    try:
        data = ZIPS[jar].read(cls.replace('.', '/') + '.class')
    except KeyError:
        _hier_cache[key] = None
        return None
    p = 10
    count = struct.unpack_from('>H', data, 8)[0]
    pool, i = {}, 1
    while i < count:
        tag = data[p]
        p += 1
        if tag == 1:
            ln = struct.unpack_from('>H', data, p)[0]
            pool[i] = data[p + 2:p + 2 + ln].decode('utf-8', 'replace')
            p += 2 + ln
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = struct.unpack_from('>H', data, p)[0]
            p += 2
        elif tag == 15:
            p += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            p += 4
        elif tag in (5, 6):
            p += 8
            i += 1
        else:
            raise ValueError('bad constant pool tag %d in %s' % (tag, cls))
        i += 1
    _hidx = struct.unpack_from('>H', data, p)[0]
    p += 2
    this_i, super_i = struct.unpack_from('>HH', data, p)
    p += 4
    n_if = struct.unpack_from('>H', data, p)[0]
    p += 2
    ifs = [struct.unpack_from('>H', data, p + 2 * k)[0] for k in range(n_if)]
    name = lambda idx: pool[pool[idx]] if pool.get(idx, 0) in pool else None
    info = {'super': name(super_i) if super_i else None,
            'interfaces': [name(k) for k in ifs]}
    _hier_cache[key] = info
    return info


def chain(jar, cls):
    """[cls, super, ...] in MRO-ish order (interfaces appended last, deduped)."""
    out, seen, ifaces = [], set(), []
    cur = cls
    while cur and cur not in seen:
        seen.add(cur)
        out.append(cur)
        info = class_info(jar, cur)
        if not info:
            break
        ifaces.extend(info['interfaces'])
        cur = info['super']
    for i in ifaces:
        if i and i not in seen:
            seen.add(i)
            out.append(i)
    return out


# --------------------------------------------------------------------------- javap
def _javap(jar, cls):
    key = jar + '|' + cls
    if key in _JAVAP_CACHE:
        return _JAVAP_CACHE[key]
    out = subprocess.run(['javap', '-p', '-s', '-cp', jar, cls],
                         capture_output=True, text=True)
    res = None if out.returncode != 0 else out.stdout
    _JAVAP_CACHE[key] = res
    return res


def members(jar, cls, deep=True):
    """{'name+desc': (signature, descriptor)} for cls, optionally including inherited members.

    javap prints a declaration line *followed by* its `descriptor:` line, so the two have to be
    paired in that order - pairing a declaration with the descriptor of the *previous* member
    silently shifts every descriptor by one and makes a field look like a method.
    """
    key = (jar, cls, deep)
    if key in _mem_cache:
        return _mem_cache[key]
    res = {}
    for c in (chain(jar, cls) if deep else [cls]):
        out = _javap(jar, c)
        if out is None:
            continue
        simple = c.rsplit('.', 1)[-1].rsplit('$', 1)[-1]
        pending = None          # (signature, name)
        for line in out.splitlines():
            s = line.strip()
            dm = re.match(r'^descriptor: (\S+)$', s)
            if dm:
                if pending is not None:
                    sig, name = pending
                    res.setdefault(name + dm.group(1), (sig, dm.group(1)))
                    res.setdefault(name, (sig, dm.group(1)))
                    pending = None
                continue
            if not s or s in ('}', '{') or not s.endswith(';'):
                continue
            head = s[:-1]
            if '(' in head:
                last = head.split('(')[0].split()[-1]
                name = '<init>' if last in (simple, c, c.rsplit('$', 1)[-1]) else last
            else:
                name = head.split()[-1]
            pending = (s, name)
    _mem_cache[key] = res
    return res


def vis(sig):
    for k in ('public', 'protected', 'private'):
        if sig.startswith(k):
            return k
    return 'package'


# --------------------------------------------------------------------------- AW
def check_aw(problems, self_test):
    n = 0
    for line in open(AW_FILE, encoding='utf-8').read().splitlines():
        line = line.split('#')[0].strip()
        if not line or not line.startswith('accessible '):
            continue
        m = re.match(r'^accessible\s+(\w+)\s+(\S+)(?:\s+(\S+))?(?:\s+(\S+))?$', line)
        if not m:
            problems.append(f'AW: unparsable line: {line}')
            continue
        kind, owner, name, desc = m.groups()
        if kind == 'class':
            # `accessible class <fqcn>` - the owner token already carries the nested name
            # (`BlockEntityType$BlockEntitySupplier`).
            fq = owner.replace('/', '.')
            if self_test and fq.endswith('BlockEntitySupplier'):
                fq += 'X'
            n += 1
            if (fq.replace('.', '/') + '.class') not in ZIPS[RAW].namelist():
                problems.append(f'AW: class {fq} is not in the raw jar')
            elif (fq.replace('.', '/') + '.class') not in ZIPS[AWED].namelist():
                problems.append(f'AW: class {fq} is missing from the AW-applied jar')
            elif _javap(AWED, fq) is None:
                problems.append(f'AW: class {fq} is still not public after widening')
            else:
                out = _javap(AWED, fq).splitlines()
                decl = next((l for l in out if ' class ' in l or l.rstrip().endswith(
                    fq.rsplit('.', 1)[-1].replace('$', '.') + ' {')), '')
                if decl and not decl.strip().startswith('public'):
                    problems.append(f'AW: class {fq} is {vis(decl.strip())} after widening')
            continue
        cls = owner.replace('/', '.')
        if self_test and name == 'PIPELINES_BY_LOCATION':
            name = 'PIPELINES_BY_LOCATION_X'
        raw, awed = members(RAW, cls), members(AWED, cls)
        if not raw or not awed:
            problems.append(f'AW: class {cls} not readable '
                            f'({"raw" if raw is None else "AW-applied"} jar)')
            continue
        n += 1
        keys = [name + desc] if desc and desc not in ('true', 'false') else [name]
        if not any(k in awed for k in keys):
            problems.append(f'AW: {cls}#{name} ({line}) is not present in the AW-applied jar '
                            f'-> Loom ignored the entry')
            continue
        key = next(k for k in keys if k in awed)
        rsig = next((raw[k][0] for k in keys if k in raw), None)
        if not awed[key][0].startswith('public'):
            problems.append(f'AW: {cls}#{name} is {vis(awed[key][0])} after widening')
        elif rsig is not None and vis(rsig) == 'public':
            problems.append(f'AW: {cls}#{name} was already public in the raw jar - no effect')
    return n


# --------------------------------------------------------------------------- mixins
def mixin_files():
    out = []
    for root, _d, files in os.walk(MIXIN_DIR):
        for f in sorted(files):
            if f.endswith('.java'):
                out.append(os.path.relpath(os.path.join(root, f), MIXIN_DIR))
    return sorted(out)


def resolve_target(rel, src, problems):
    m = re.search(r'@Mixin\(\s*([\w.]+)\.class\s*\)', src)
    if not m:
        problems.append(f'MIXIN {rel}: no @Mixin(X.class) target found')
        return None
    spec = m.group(1)
    imps = re.findall(r'^import\s+(?:static\s+)?([\w.]+);', src, re.M)
    head, _, rest = spec.partition('.')
    cands = [i for i in imps if i.rsplit('.', 1)[1] == head]
    if not cands:
        if '.' in spec:
            problems.append(f'MIXIN {rel}: cannot resolve the import of @Mixin({spec}.class)')
            return None
        return spec
    return max(cands, key=len) + (('.' + rest) if rest else '')


def split_target(spec):
    """Mixin target spec -> (owner class or None, member name or None, descriptor or None)."""
    if not spec:
        return None, None, None
    if not spec.startswith('L'):
        name, _, desc = spec.partition('(')
        return None, name, ('(' + desc if desc else None)
    body = spec[1:]
    if body.endswith(';'):
        body = body[:-1]
    if ':' in body:
        body, desc = body.split(':', 1)
        return body.replace('/', '.'), None, desc
    if ';' in body:
        owner, _, rem = body.partition(';')
        name = rem.partition('(')[0]
        d = rem.partition('(')[2]
        return owner.replace('/', '.'), name, ('(' + d if d else None)
    return body.replace('/', '.'), None, None


def check_mixins(problems, self_test):
    files = mixin_files()
    classes = defaultdict(list)
    n = 0
    for rel in files:
        src = open(os.path.join(MIXIN_DIR, rel), encoding='utf-8').read()
        cls = resolve_target(rel, src, problems)
        if cls is None:
            continue
        if not members(RAW, cls):
            nested = _nested_of(RAW, cls)
            if nested:
                cls = nested
            else:
                problems.append(f'MIXIN: target class {cls} is not in the jar ({rel})')
                continue
        classes[cls].append(rel)
        for m in re.finditer(r'@(' + '|'.join(ANNOTATIONS) + r')\s*\(', src):
            args = balanced(src, m.end() - 1)
            dm = re.search(r'method\s*=\s*"([^"]+)"', args)
            if not dm:
                problems.append(f'MIXIN {rel}: @{m.group(1)} without method= (args {args[:70]!r})')
                continue
            spec = dm.group(1)
            if not spec.strip():
                continue
            if self_test:
                spec = spec.partition('(')[0] + 'X' + ('(' + spec.partition('(')[2]
                                                       if '(' in spec else '')
            raw = members(RAW, cls)
            n += 1
            ocls, name, desc = split_target(spec)
            if ocls:
                if not members(RAW, ocls):
                    problems.append(f'MIXIN {rel}: {spec} - owner class {ocls} not in jar')
                continue
            if name not in raw:
                problems.append(f'MIXIN {rel}: {cls}#{name} does not exist (@{m.group(1)})')
            elif desc and not any(k.split('(')[0] == name and v[1] == desc
                                  for k, v in raw.items()):
                problems.append(f'MIXIN {rel}: {cls}#{name} has no overload {desc} '
                                f'(@{m.group(1)})')
        for tm in re.finditer(r'target\s*=\s*"([^"]+)"', src):
            ocls, name, desc = split_target(tm.group(1))
            if ocls is None:
                continue
            if not members(RAW, ocls):
                problems.append(f'MIXIN {rel}: @At target owner {ocls} not in jar')
                continue
            if name and not any(k.split('(')[0] == name for k in members(RAW, ocls)):
                problems.append(f'MIXIN {rel}: @At target {ocls}#{name} does not exist')
    return len(classes), n, len(files)


def _nested_of(jar, cls):
    """Progressively turn the trailing `.Inner` of a nested-class reference into `Outer$Inner`."""
    parts = cls.split('.')
    for cut in range(len(parts) - 1, 0, -1):
        cand = '.'.join(parts[:cut]) + '$' + '$'.join(parts[cut:])
        if (cand.replace('.', '/') + '.class') in ZIPS[jar].namelist():
            return cand
    return None


def balanced(src, i):
    """src[i] == '('; return the text between the parentheses."""
    start, depth, in_str = i + 1, 0, False
    while i < len(src):
        ch = src[i]
        if in_str:
            if ch == '\\':
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return src[start:i]
        i += 1
    return src[start:]


def main(self_test):
    problems = []
    aw_n = check_aw(problems, self_test)
    cls_n, inj_n, file_n = check_mixins(problems, self_test)
    _save_cache()
    print(f'AW entries checked  : {aw_n}')
    print(f'mixin files / classes: {file_n} / {cls_n}')
    print(f'mixin injectors     : {inj_n}')
    if problems:
        print(f'\nPROBLEMS ({len(problems)}):')
        for p in problems:
            print('  -', p)
    else:
        print('\nNo problems found.')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main('--self-test' in sys.argv))
