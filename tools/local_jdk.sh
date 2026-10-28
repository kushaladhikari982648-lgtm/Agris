#!/usr/bin/env bash
# Fetch a local Java compiler for sandboxes that cannot reach Maven or the Adoptium CDN.
#
#   * JRE 25   — the jdk4py wheel on PyPI (java.base + java.compiler, but no javac)
#   * ECJ 3.45 — the Eclipse batch compiler bundled in @vscjava/java-language-server on npm
#
# The result lands in ${AETHERIUM_TC:-/tmp/tc}: jre/bin/java and ecj.jar. Usage afterwards:
#   /tmp/tc/jre/bin/java -jar /tmp/tc/ecj.jar -source 8 -target 8 -d out $(find src -name '*.java')
# tools/stubcheck.py drives this against signature stubs generated from tools/probe/*.txt.
set -euo pipefail
TC="${AETHERIUM_TC:-/tmp/tc}"
if [ -x "$TC/jre/bin/java" ] && [ -f "$TC/ecj.jar" ]; then
  echo "toolchain present in $TC"; exit 0
fi
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$TC"

pip download jdk4py --no-deps -q -d "$work"
(cd "$work" && unzip -q -o jdk4py-*.whl 'jdk4py/java-runtime/*')
rm -rf "$TC/jre" && mv "$work/jdk4py/java-runtime" "$TC/jre"
chmod +x "$TC"/jre/bin/*

tarball="$(curl -s https://registry.npmjs.org/@vscjava/java-language-server \
  | python3 -c "import sys,json;d=json.load(sys.stdin);v=d['dist-tags']['latest'];print(d['versions'][v]['dist']['tarball'])")"
curl -s -o "$work/jls.tgz" "$tarball"
entry="$(tar tzf "$work/jls.tgz" | grep -E 'plugins/org\.eclipse\.jdt\.core\.compiler\.batch_.*\.jar$' | head -1)"
tar xzf "$work/jls.tgz" -C "$work" "$entry"
cp "$work/$entry" "$TC/ecj.jar"

"$TC/jre/bin/java" -jar "$TC/ecj.jar" -version
