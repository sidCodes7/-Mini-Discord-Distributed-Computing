#!/usr/bin/env bash
# Automated demo for EXP 2. Usage: ./scripts/demo-exp2.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 2 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
CP=build/exp2
java -cp $CP exp2.ConcurrencyDemo || exit 1
echo
java -cp $CP exp2.RaceConditionDemo || exit 1
