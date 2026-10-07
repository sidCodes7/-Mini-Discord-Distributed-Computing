#!/usr/bin/env bash
# Automated demo for EXP 3. Usage: ./scripts/demo-exp3.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 3 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
java -cp build/exp3 exp3.Exp3Demo
