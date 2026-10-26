#!/usr/bin/env python3
"""Compile Agris (common + fabric) against offline Minecraft 1.20.1 signature stubs."""
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import eras  # noqa: E402
import stubgen  # noqa: E402

TC = os.environ.get("AGRIS_TC", os.environ.get("AETHERIUM_TC", "/tmp/tc"))
STUBS = os.environ.get("AGRIS_STUBS", "/tmp/agris-stubs")
WORK = os.environ.get("AGRIS_CHECK", "/tmp/agris-stubcheck")
JAVA = os.path.join(TC, "jre", "bin", "java")
ECJ = os.path.join(TC, "ecj.jar")

# Ensure stubgen writes to STUBS too.
stubgen.OUT = STUBS
stubgen.TC = TC
stubgen.JAVA = JAVA
stubgen.ECJ = ECJ


def rows():
    data = json.load(open(os.path.join(ROOT, "tools", "porting_pins.json"), encoding="utf-8"))
    data = data["rows"] if isinstance(data, dict) and "rows" in data else data
    return {r["version"]: r for r in data}


def ecj(args):
    r = subprocess.run([JAVA, "-jar", ECJ] + args, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def lib_classes():
    out = os.path.join(STUBS, "_lib")
    stamp = os.path.join(out, ".stamp")
    src = os.path.join(ROOT, "tools", "stubs_lib")
    newest = max(os.path.getmtime(os.path.join(dp, f)) for dp, _, fs in os.walk(src) for f in fs)
    if os.path.exists(stamp) and os.path.getmtime(stamp) >= newest:
        return out
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out, exist_ok=True)
    files = [os.path.join(dp, f) for dp, _, fs in os.walk(src) for f in fs if f.endswith(".java")]
    code, log = ecj(["-source", "8", "-target", "8", "-nowarn", "-d", out] + files)
    if code != 0:
        print(log)
        raise SystemExit("library stubs failed to compile")
    open(stamp, "w").close()
    return out


def mc_classes(version):
    out = os.path.join(STUBS, version, "classes")
    dump = os.path.join(ROOT, "tools", "probe", version + ".txt")
    if not os.path.isdir(out) or os.path.getmtime(out) < os.path.getmtime(dump) \
            or os.path.getmtime(out) < os.path.getmtime(os.path.join(ROOT, "tools", "stubgen.py")):
        stubgen.compile_stubs(version)
    return out


def source_roots(row):
    roots = [
        os.path.join(ROOT, "common", "src", "main", "java"),
        os.path.join(ROOT, "fabric", "src", "main", "java"),
    ]
    if row.get("neoforge"):
        roots.append(os.path.join(ROOT, "neoforge", "src", "main", "java"))
    return roots


def prepare(version, row):
    dest = os.path.join(WORK, version, "src")
    shutil.rmtree(os.path.join(WORK, version), ignore_errors=True)
    used = set()
    files = []
    for root in source_roots(row):
        for dp, _, fs in os.walk(root):
            for f in fs:
                if not f.endswith(".java"):
                    continue
                src = os.path.join(dp, f)
                rel = os.path.relpath(src, root)
                text = open(src, encoding="utf-8").read()
                if eras.has_markers(text):
                    text = eras.select_all(text, version, os.path.relpath(src, ROOT), used)
                out = os.path.join(dest, rel)
                os.makedirs(os.path.dirname(out), exist_ok=True)
                with open(out, "w", encoding="utf-8") as h:
                    h.write(text)
                files.append(sorted_path := out)
    return sorted(files), used


def check(version, row, verbose=True):
    java = str(min(int(row.get("java", 21)), 24))
    files, _ = prepare(version, row)
    cp = os.pathsep.join([lib_classes(), mc_classes(version)])
    out = os.path.join(WORK, version, "classes")
    os.makedirs(out, exist_ok=True)
    lst = os.path.join(WORK, version, "files.txt")
    with open(lst, "w", encoding="utf-8") as h:
        h.write("\n".join(files))
    code, log = ecj([
        "-source", java, "-target", java, "-nowarn", "-encoding", "UTF-8",
        "-cp", cp, "-d", out, "@" + lst
    ])
    log = log.replace(os.path.join(WORK, version, "src") + os.sep, "")
    errors = len(re.findall(r"^\d+\. ERROR", log, re.M))
    import java_api_lint
    api = java_api_lint.lint_files(files, int(row.get("java", 21)))
    if api:
        errors += len(api)
        log += "\n" + "\n".join("API-LEVEL ERROR " + p for p in api) + "\n"
    with open(os.path.join(WORK, version, "errors.txt"), "w", encoding="utf-8") as h:
        h.write(log)
    status = "ok" if errors == 0 else "%d errors" % errors
    print("%-8s java %-2s %-4d files  %s" % (version, java, len(files), status))
    if errors and verbose:
        print(log if len(log) < 20000 else log[:20000])
    return errors


def main():
    args = sys.argv[1:]
    quiet = "-q" in args
    args = [a for a in args if a != "-q"]
    table = rows()
    targets = sorted(table, key=eras.vkey) if args == ["--all"] or not args else args
    failed = 0
    for v in targets:
        if v not in table:
            raise SystemExit("unknown version %s" % v)
        if check(v, table[v], verbose=not quiet):
            failed += 1
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
