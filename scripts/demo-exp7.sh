#!/usr/bin/env bash
# Automated demo for EXP 7. Usage: ./scripts/demo-exp7.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 7 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
java -cp build/exp7 exp7.Exp7Demo
