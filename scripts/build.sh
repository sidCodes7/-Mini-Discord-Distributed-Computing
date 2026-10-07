#!/usr/bin/env bash
# Compile every experiment (each one together with the shared common/ sources).
# Output: build/exp0 ... build/exp7   Usage: ./scripts/build.sh [expNumber]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ $# -ge 1 ]; then LIST="$1"; else LIST="0 1 2 3 4 5 6 7"; fi
for n in $LIST; do
    dir=$(ls -d exp"$n"-* | head -1)
    rm -rf "build/exp$n"
    mkdir -p "build/exp$n"
    javac -d "build/exp$n" $(find common/src "$dir/src" -name '*.java')
    echo "compiled exp$n ($dir) -> build/exp$n"
done
