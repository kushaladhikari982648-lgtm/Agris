#!/usr/bin/env python3
"""Flags Java language features and JDK APIs that are newer than a row's Java level.

Why this exists: tools/stubcheck.py compiles with ECJ at ``-source N`` on a modern
runtime. ``-source`` gates *syntax* but not the *class library*, so a call such as
``Optional.isEmpty()`` (Java 11) compiles offline for the Java 8 row and only fails on
CI's real JDK 8 (it did: the first 0.2.0 ship run, 1.16.5). ECJ's ``--release`` would fix
this but needs a JDK's ct.sym, which no reachable host provides.

The rules are lexical, run on the era-selected sources with comments and string literals
blanked out, and only cover APIs whose names do not collide with Java 8 methods
(e.g. ``BufferedReader.lines()`` and ``Deque.getFirst()`` are Java 8, so ``lines()`` and
``getFirst()`` are not rules). Optional-only methods are checked on identifiers declared
as ``Optional<...>`` in the same file, plus direct ``Optional.x(...).m()`` chains.

Usage: python3 tools/java_api_lint.py <java-level> <file.java> ...
"""
from __future__ import annotations

import re
import sys

# (minimum Java level, regex, description)
RULES: list[tuple[int, str, str]] = [
    (9, r"\b(?:List|Set|Map)\.of\s*\(", "List/Set/Map.of"),
    (9, r"\bMap\.(?:entry|ofEntries)\s*\(", "Map.entry/ofEntries"),
    (9, r"\bObjects\.requireNonNullElse(?:Get)?\s*\(", "Objects.requireNonNullElse"),
    (9, r"\bObjects\.checkIndex\s*\(", "Objects.checkIndex"),
    (9, r"\.(?:takeWhile|dropWhile)\s*\(", "Stream.takeWhile/dropWhile"),
    (9, r"\bStream\.ofNullable\s*\(", "Stream.ofNullable"),
    (9, r"\w\s*\.readAllBytes\s*\(\s*\)", "InputStream.readAllBytes()"),
    (9, r"\.transferTo\s*\(", "InputStream.transferTo"),
    (9, r"\bStackWalker\b|\bProcessHandle\b", "StackWalker/ProcessHandle"),
    (9, r"\.ifPresentOrElse\s*\(", "Optional.ifPresentOrElse"),
    (10, r"(?:^|[;{}(]\s*|\bfinal\s+)var\s+\w+\s*[=:]", "local variable type inference (var)"),
    (10, r"\b(?:List|Set|Map)\.copyOf\s*\(", "List/Set/Map.copyOf"),
    (10, r"\bCollectors\.toUnmodifiable\w*\s*\(", "Collectors.toUnmodifiable*"),
    (11, r"\.(?:isBlank|strip|stripLeading|stripTrailing)\s*\(\s*\)", "String.isBlank/strip"),
    (11, r"\.repeat\s*\(", "String.repeat / StringBuilder.repeat"),
    (11, r"\bPath\.of\s*\(", "Path.of"),
    (11, r"\bFiles\.(?:readString|writeString)\s*\(", "Files.readString/writeString"),
    (11, r"\bPredicate\.not\s*\(", "Predicate.not"),
    (12, r"\bCollectors\.teeing\s*\(", "Collectors.teeing"),
    (12, r"\.(?:indent|transform)\s*\(", "String.indent/transform"),
    (14, r"\bcase\b[^:;{}]*->", "switch arrow case"),
    (14, r"=\s*switch\s*\(|return\s+switch\s*\(", "switch expression"),
    (15, r'"""', "text block"),
    (16, r"\brecord\s+[A-Z]\w*\s*(?:<[^>]*>)?\s*\(", "record"),
    (16, r"\binstanceof\s+(?:final\s+)?[\w.]+(?:<[^>]*>)?\s+[a-z]\w*\s*(?:[)&|;?:]|$)", "instanceof pattern"),
    (16, r"\.toList\s*\(\s*\)", "Stream.toList()"),
    (17, r"\bsealed\s+(?:class|interface)\b|\bnon-sealed\b", "sealed types"),
    (21, r"\bMath\.clamp\s*\(", "Math.clamp"),
    (21, r"\bThread\.ofVirtual\s*\(", "virtual threads"),
]

# Optional-only members, by the Java level that added them.
OPTIONAL_MEMBERS = {"isEmpty": 11, "orElseThrow()": 10, "or": 9, "stream": 9, "ifPresentOrElse": 9}

_STRIP = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)


def _blank(text: str) -> str:
    """Comments and string/char literals -> spaces (newlines kept so line numbers hold)."""
    def repl(match: re.Match) -> str:
        token = match.group(0)
        if token.startswith('"""'):
            return token
        if token.startswith('"'):
            return '""'
        return re.sub(r"[^\n]", " ", token)
    return _STRIP.sub(repl, text)


def lint_text(text: str, level: int) -> list[tuple[int, str]]:
    src = _blank(text)
    problems: list[tuple[int, str]] = []

    def line_of(pos: int) -> int:
        return src.count("\n", 0, pos) + 1

    for need, pattern, what in RULES:
        if level >= need:
            continue
        for match in re.finditer(pattern, src, re.M):
            problems.append((line_of(match.start()), f"{what} needs Java {need}"))

    optional_names = set(re.findall(r"\bOptional\s*<[^;=()]*?>\s+(\w+)\s*[=;,)]", src))
    for member, need in OPTIONAL_MEMBERS.items():
        if level >= need:
            continue
        name = member.rstrip("()")
        call = r"\s*\(\s*\)" if member.endswith("()") else r"\s*\("
        receivers = [re.escape(n) for n in sorted(optional_names)]
        receivers.append(r"Optional\.\w+\([^;]*?\)")
        pattern = r"\b(?:" + "|".join(receivers) + r")\s*\.\s*" + name + call
        for match in re.finditer(pattern, src):
            problems.append((line_of(match.start()), f"Optional.{member} needs Java {need}"))
    return sorted(set(problems))


def lint_files(paths: list[str], level: int) -> list[str]:
    out = []
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            for line, msg in lint_text(handle.read(), level):
                out.append(f"{path}:{line}: {msg}")
    return out


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    problems = lint_files(sys.argv[2:], int(sys.argv[1]))
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
