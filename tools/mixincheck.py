#!/usr/bin/env python3
"""Verify Agris Mixin targets, descriptors, and bytecode call-sites against tools/probe/<ver>.txt."""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import eras  # noqa: E402

SRC = os.path.join(ROOT, "common", "src", "main", "java")
MIXINS_JSON = os.path.join(ROOT, "common", "src", "main", "resources", "agris-common.mixins.json")
PRIMS = {"Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long",
         "F": "float", "D": "double", "V": "void"}
INJECTORS = ("Inject", "Redirect", "ModifyReturnValue", "WrapOperation", "ModifyArg", "ModifyArgs",
             "ModifyVariable", "ModifyConstant", "WrapWithCondition", "ModifyExpressionValue",
             "WrapMethod")

# Optional runtime-gated third-party targets (checked by AgrisMixinPlugin before Mixin loads them)
OPTIONAL_EXTERNAL_TARGETS = {
    "com.aetherium.client.AetheriumPages",
}


def sig_name(sig):
    m = re.search(r"([\w$<>]+)\(", sig)
    return m.group(1) if m else ""


def parse_probe(path):
    classes, bodies, invoked_descs = {}, {}, {}
    cur, mode, mcls, mbody = None, None, None, None
    code_bodies, section = {}, 0
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            c = line.find("// ")
            if c >= 0:
                cm = re.match(r"(?:Method|InterfaceMethod)\s+(\S+?):(\S+)$", line[c + 3:])
                if cm and "." in cm.group(1):
                    rowner, rname = cm.group(1).rsplit(".", 1)
                    rowner = rowner.strip('"').replace("/", ".")
                    invoked_descs.setdefault(rowner, set()).add(rname + cm.group(2))
            if line.startswith("=== method ") or line.startswith("=== code "):
                mode = "method" if line.startswith("=== method ") else "code"
                mcls = line.split()[2]
                section += 1
                cur, mbody = None, None
            elif line.startswith("=== "):
                mode, mbody = None, None
                cur = line[4:].strip()
                classes.setdefault(cur, [])
            elif mode == "method":
                if line.startswith("##"):
                    mbody = []
                    bodies.setdefault(mcls, {}).setdefault(sig_name(line), []).append((mbody, True))
                elif mbody is not None:
                    mbody.append(line)
            elif mode == "code":
                if " :: " in line:
                    sig, instr = line.split(" :: ", 1)
                    key = (mcls, sig_name(sig), section)
                    if key not in code_bodies:
                        code_bodies[key] = []
                        bodies.setdefault(mcls, {}).setdefault(sig_name(sig), []).append((code_bodies[key], False))
                    code_bodies[key].append(instr)
            elif cur is not None:
                classes[cur].append(line.strip())
    return classes, bodies, invoked_descs


def java_type(desc, i):
    dims = 0
    while desc[i] == "[":
        dims += 1
        i += 1
    if desc[i] == "L":
        j = desc.index(";", i)
        name = desc[i + 1:j].replace("/", ".")
        i = j + 1
    else:
        name = PRIMS[desc[i]]
        i += 1
    return name + "[]" * dims, i


def params_of(desc):
    inner = desc[desc.index("(") + 1:desc.index(")")]
    out, i = [], 0
    while i < len(inner):
        t, i = java_type(inner, i)
        out.append(t)
    return out


def member_methods(lines):
    out = {}
    for ln in lines:
        m = re.search(r"([\w$<>]+)\(([^)]*)\)(?:\s+throws [^;]*)?;$", ln)
        if not m:
            continue
        name = m.group(1)
        if not re.search(r"(^|[ .])%s\(" % re.escape(name), ln):
            continue
        params = [p.strip() for p in m.group(2).split(",")] if m.group(2).strip() else []
        params = [re.sub(r"<.*>", "", p) for p in params]
        params = [p[:-3] + "[]" if p.endswith("...") else p for p in params]
        out.setdefault(name, []).append(params)
    return out


def member_fields(lines):
    out = set()
    for ln in lines:
        if "(" in ln:
            continue
        fm = re.search(r"\s([\w$]+);$", ln)
        if fm:
            out.add(fm.group(1))
    return out


def resolve_class(expr, imports, pkg):
    expr = expr.strip()
    if expr.endswith(".class"):
        expr = expr[:-6]
    parts = expr.split(".")
    if parts[0] in imports:
        return imports[parts[0]] + "".join("$" + p for p in parts[1:])
    if re.match(r"^[a-z]", parts[0]):
        return expr
    return pkg + "." + expr


def balanced(text, start):
    depth, i, in_str = 1, start, False
    while depth and i < len(text):
        c = text[i]
        if in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        i += 1
    return text[start:i - 1]


def annotations(text, names):
    for m in re.finditer(r"@(%s)\s*\(" % "|".join(names), text):
        yield m.group(1), balanced(text, m.end())


def strings_of(arg, key):
    m = re.search(r"\b%s\s*=\s*(\{[^}]*\}|\"(?:[^\"\\]|\\.)*\")" % key, arg)
    if not m:
        return []
    return re.findall(r"\"((?:[^\"\\]|\\.)*)\"", m.group(1))


def ats_of(arg):
    out = []
    for m in re.finditer(r"@At\s*\(", arg):
        inner = balanced(arg, m.end())
        val = strings_of(inner, "value") or re.findall(r"^\s*\"([^\"]*)\"", inner)
        tgt = strings_of(inner, "target")
        om = re.search(r"\bordinal\s*=\s*(\d+)", inner)
        out.append((val[0] if val else "", tgt[0] if tgt else "", int(om.group(1)) if om else -1))
    return out


def site_count(body, target, owner_cls):
    m = re.match(r"L([^;]+);([\w$<>]+)(?::(\S+)|(\(.*))?$", target)
    if not m:
        return 0
    owner, name = m.group(1), m.group(2)
    desc = m.group(3) or m.group(4) or ""
    self_owner = owner_cls.replace(".", "/")
    n = 0
    for ln in body:
        c = ln.find("// ")
        if c < 0:
            continue
        cm = re.match(r"(?:Method|InterfaceMethod|Field)\s+(\S+?):(\S+)$", ln[c + 3:])
        if not cm:
            continue
        ref, rdesc = cm.group(1), cm.group(2)
        if "." in ref:
            rowner, rname = ref.rsplit(".", 1)
            rowner = rowner.strip('"')
        else:
            rowner, rname = self_owner, ref
        if rname != name or rowner != owner:
            continue
        if desc and rdesc != desc:
            continue
        n += 1
    return n


def main():
    argv = sys.argv[1:]
    quiet = "--quiet" in argv
    args = [a for a in argv if not a.startswith("--")]
    ver = args[0] if args else "1.20.1"
    classes, bodies, invoked_descs = parse_probe(os.path.join(ROOT, "tools", "probe", ver + ".txt"))
    with open(MIXINS_JSON, encoding="utf-8") as fh:
        cfg = json.load(fh)
    pkg = cfg["package"]
    names = cfg.get("mixins", []) + cfg.get("client", [])
    bad = 0
    counts = {}

    def report(tag, msg):
        nonlocal bad
        counts[tag] = counts.get(tag, 0) + 1
        if tag in ("MISSING", "NO-SITE", "OVERWRITE"):
            bad += 1
        if tag != "OK" or not quiet:
            print("%-10s %s" % (tag, msg))

    for name in names:
        path = os.path.join(SRC, *(pkg + "." + name).split(".")) + ".java"
        if not os.path.exists(path):
            report("MISSING", "%s: no source file" % name)
            continue
        raw_text = open(path, encoding="utf-8").read()
        if "@Overwrite" in raw_text:
            report("OVERWRITE", "%s uses forbidden @Overwrite" % name)
        text = eras.select_all(raw_text, ver)
        text = "\n".join(l for l in text.split("\n") if not l.lstrip().startswith("//"))
        imports = {}
        for im in re.findall(r"^import\s+([\w.]+);", text, re.M):
            imports[im.rsplit(".", 1)[1]] = im
        fpkg = re.search(r"^package\s+([\w.]+);", text, re.M).group(1)
        mm = re.search(r"@Mixin\s*\(", text)
        if not mm:
            continue
        marg = balanced(text, mm.end())
        targets = strings_of(marg, "targets")
        if not targets:
            vm = re.findall(r"([\w.]+)\.class", marg)
            targets = [resolve_class(x, imports, fpkg) for x in vm]
        targets = [t.replace("/", ".") for t in targets]
        for tcls in targets:
            if tcls in OPTIONAL_EXTERNAL_TARGETS:
                report("OPTIONAL", "%-28s %s (plugin-gated)" % (name, tcls))
                continue
            if tcls not in classes:
                known_calls = invoked_descs.get(tcls, set())
                if not known_calls:
                    report("MISSING", "%-28s %s (unprobed target)" % (name, tcls))
                    continue
                for ann, arg in annotations(text, INJECTORS):
                    for meth in strings_of(arg, "method"):
                        if meth in known_calls:
                            report("OK", "%-28s %s :: %s (verified via bytecode call-site)" % (name, tcls, meth))
                        else:
                            report("MISSING", "%-28s %s :: %s (not in bytecode call-sites %s)" % (name, tcls, meth, sorted(known_calls)))
                continue
            lines = classes[tcls]
            members = member_methods(lines)
            fields = member_fields(lines)
            simple = tcls.rsplit(".", 1)[1]
            for sm in re.finditer(r"@Shadow\b(?:\s*\([^)]*\))?((?:\s*@\w+(?:\([^)]*\))?)*)\s*"
                                  r"((?:public|protected|private|static|final|abstract|\s)+)"
                                  r"([\w.<>\[\]$, ?]+?)\s+([\w$]+)\s*([(;=])", text):
                mname, kind = sm.group(4), sm.group(5)
                ok = (mname in members) if kind == "(" else (mname in fields)
                report("OK" if ok else "MISSING", "%-28s %s :: @Shadow %s" % (name, tcls, mname))
            for kind, arg in annotations(text, ("Accessor", "Invoker")):
                vals = re.findall(r"\"([^\"]+)\"", arg)
                if not vals:
                    continue
                mname = vals[0]
                ok = (mname in fields) if kind == "Accessor" else (mname in members or mname == "<init>")
                report("OK" if ok else "MISSING", "%-28s %s :: @%s %s" % (name, tcls, kind, mname))
            for ann, arg in annotations(text, INJECTORS):
                for meth in strings_of(arg, "method"):
                    mname = meth.split("(")[0]
                    desc = meth[len(mname):]
                    if mname == "<init>":
                        found = [p for n2, ps in members.items() for p in ps if n2 in (tcls, simple)]
                    else:
                        found = members.get(mname, [])
                    if not found:
                        report("MISSING", "%-28s %s :: %s" % (name, tcls, meth))
                        continue
                    if desc:
                        want = params_of(desc)
                        if not any(len(f) == len(want) and all(a == b or re.match(r"^[A-Z]\w?$", b)
                                                               for a, b in zip(want, f)) for f in found):
                            report("MISSING", "%-28s %s :: %s (have %s)" % (name, tcls, meth, found))
                            continue
                    for val, tgt, ordinal in ats_of(arg):
                        if val not in ("INVOKE", "FIELD", "INVOKE_ASSIGN") or not tgt:
                            report("OK", "%-28s %s :: %s @%s" % (name, tcls, meth, val or "?"))
                            continue
                        dumps = bodies.get(tcls, {}).get(mname)
                        if not dumps:
                            report("UNVERIFIED", "%-28s %s :: %s -> %s (no dump)" % (name, tcls, meth, tgt))
                            continue
                        best, complete = 0, False
                        for body, full in dumps:
                            complete = complete or full
                            best = max(best, site_count(body, tgt, tcls))
                        need = max(ordinal, 0) + 1
                        if best >= need:
                            report("OK", "%-28s %s :: %s -> %s x%d" % (name, tcls, meth, tgt.split(";", 1)[-1], best))
                        elif complete:
                            report("NO-SITE", "%-28s %s :: %s -> %s (found %d, need %d)" % (name, tcls, meth, tgt, best, need))
                        else:
                            report("UNVERIFIED", "%-28s %s :: %s -> %s (partial dump: found %d)" % (name, tcls, meth, tgt, best))
    print("summary:", ", ".join("%s %d" % kv for kv in sorted(counts.items())))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
