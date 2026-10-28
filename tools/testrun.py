#!/usr/bin/env python3
"""Compile and run Agris JUnit 5 unit tests against offline stubs + local JRE."""
import argparse
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import stubcheck  # noqa: E402

TESTS = os.path.join(ROOT, "common", "src", "test", "java")
MINIJUNIT = os.path.join(ROOT, "tools", "minijunit")


def java_files(root):
    if not os.path.isdir(root):
        return []
    out = []
    for base, _, files in os.walk(root):
        out.extend(os.path.join(base, f) for f in files if f.endswith(".java"))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="1.20.1")
    ap.add_argument("only", nargs="*")
    args = ap.parse_args()
    version = args.version
    row = stubcheck.rows()[version]
    if stubcheck.check(version, row, verbose=True):
        return 1
    java = str(min(int(row.get("java", 21)), 24))
    main_classes = os.path.join(stubcheck.WORK, version, "classes")
    out = os.path.join(stubcheck.WORK, version, "test-classes")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    cp = os.pathsep.join([main_classes, stubcheck.lib_classes(), stubcheck.mc_classes(version)])
    files = java_files(MINIJUNIT) + java_files(TESTS)
    if not java_files(TESTS):
        print("No tests found in", TESTS)
        return 1
    lst = os.path.join(stubcheck.WORK, version, "test-files.txt")
    with open(lst, "w", encoding="utf-8") as h:
        h.write("\n".join(files))
    code, log = stubcheck.ecj([
        "-source", java, "-target", java, "-nowarn", "-encoding", "UTF-8",
        "-proceedOnError", "-cp", cp, "-d", out, "@" + lst
    ])
    errors = len(re.findall(r"^\d+\. ERROR", log, re.M))
    if errors:
        print(log.replace(TESTS + os.sep, "")[:30000])
        print("%d test compile errors" % errors)
        return 1
    classes = []
    for f in java_files(TESTS):
        text = open(f, encoding="utf-8").read()
        if "@Test" not in text:
            continue
        name = os.path.relpath(f, TESTS)[:-5].replace(os.sep, ".")
        if args.only and name.rsplit(".", 1)[-1] not in args.only:
            continue
        classes.append(name)
    res = os.path.join(ROOT, "common", "src", "main", "resources")
    run_cp = os.pathsep.join([out, main_classes, res, stubcheck.lib_classes(), stubcheck.mc_classes(version)])
    r = subprocess.run([stubcheck.JAVA, "-cp", run_cp, "org.junit.platform.MiniRunner"] + classes, cwd=ROOT)
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
