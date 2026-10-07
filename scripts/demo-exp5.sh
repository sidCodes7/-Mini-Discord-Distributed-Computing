#!/usr/bin/env bash
# Automated demo for EXP 5. Usage: ./scripts/demo-exp5.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 5 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
java -cp build/exp5 exp5.Exp5Demo
