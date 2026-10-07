#!/usr/bin/env bash
# Automated demo for EXP 6. Usage: ./scripts/demo-exp6.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 6 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
java -cp build/exp6 exp6.Exp6Demo "${1:-both}"
