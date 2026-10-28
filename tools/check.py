#!/usr/bin/env python3
"""Master verification gate for Agris (hygiene, mixin sync, schema sync, ECJ compile, mixin bytecode check, JUnit 5)."""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def iter_own_java_files():
    for module in ("common", "fabric"):
        src_root = os.path.join(ROOT, module, "src")
        if not os.path.isdir(src_root):
            continue
        for dp, _, fs in os.walk(src_root):
            for f in sorted(fs):
                if f.endswith(".java"):
                    yield os.path.join(dp, f)


def check_hygiene() -> list[str]:
    problems = []
    todo_re = re.compile(r"\b(TODO|FIXME|XXX)\b")
    for path in iter_own_java_files():
        rel = os.path.relpath(path, ROOT)
        raw = open(path, "rb").read()
        if b"\r\n" in raw:
            problems.append(f"{rel}: CRLF line endings found")
        text = raw.decode("utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            if "\t" in line:
                problems.append(f"{rel}:{lineno}: tab character found")
            if todo_re.search(line):
                problems.append(f"{rel}:{lineno}: unfinished marker found ({line.strip()})")
            if "@Overwrite" in line and not line.lstrip().startswith("*") and not line.lstrip().startswith("//"):
                problems.append(f"{rel}:{lineno}: forbidden @Overwrite annotation")
    return problems


def check_mixin_registration() -> list[str]:
    problems = []
    mixins_json = os.path.join(ROOT, "common", "src", "main", "resources", "agris-common.mixins.json")
    cfg = json.load(open(mixins_json, encoding="utf-8"))
    declared = set(cfg.get("client", []) + cfg.get("mixins", []))
    mixin_dir = os.path.join(ROOT, "common", "src", "main", "java", "com", "agris", "mixin", "core")
    on_disk = {f[:-5] for f in os.listdir(mixin_dir) if f.endswith(".java")}
    missing_in_json = sorted(on_disk - declared)
    missing_on_disk = sorted(declared - on_disk)
    if missing_in_json:
        problems.append(f"Mixin classes missing from agris-common.mixins.json: {missing_in_json}")
    if missing_on_disk:
        problems.append(f"Mixin classes declared in JSON but missing on disk: {missing_on_disk}")
    return problems


def check_schema_sync() -> list[str]:
    schema_path = os.path.join(ROOT, "CONFIG_SCHEMA.json")
    if not os.path.isfile(schema_path):
        return ["CONFIG_SCHEMA.json is missing"]
    schema = json.load(open(schema_path, encoding="utf-8"))
    cfg_java = open(
        os.path.join(ROOT, "common", "src", "main", "java", "com", "agris", "config", "AgrisConfig.java"),
        encoding="utf-8",
    ).read()
    found_keys = re.findall(r'ConfigValue\.(?:bool|intRange|string|enumerated)\(\s*"([^"]+)"', cfg_java)
    schema_keys = [o["key"] for o in schema.get("options", [])]
    if found_keys != schema_keys:
        return [f"CONFIG_SCHEMA.json keys {schema_keys} != AgrisConfig keys {found_keys}"]
    return []


def run_step(label: str, cmd: list[str]) -> bool:
    print(f"==> {label}")
    r = subprocess.run(cmd, cwd=ROOT)
    if r.returncode != 0:
        print(f"FAILED: {label} (exit {r.returncode})")
        return False
    return True


def main() -> int:
    all_problems = []
    all_problems.extend(check_hygiene())
    all_problems.extend(check_mixin_registration())
    all_problems.extend(check_schema_sync())
    if all_problems:
        for p in all_problems:
            print("ERROR:", p)
        return 1
    print("==> Hygiene, mixin registration & CONFIG_SCHEMA.json checks: OK")

    if not run_step("Mixin target & bytecode call-site verification (Fabric MC 1.20.1)",
                    [sys.executable, "tools/mixincheck.py", "1.20.1", "--quiet"]):
        return 1
    if not run_step("Offline Fabric 1.20.1 Java 17 compile + JUnit 5 suite (common + fabric)",
                    [sys.executable, "tools/testrun.py"]):
        return 1

    print("ALL AGRIS VERIFICATION GATES PASSED.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
