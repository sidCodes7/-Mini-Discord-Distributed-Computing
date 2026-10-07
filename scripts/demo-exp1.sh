#!/usr/bin/env bash
# Automated demo for EXP 1. Usage: ./scripts/demo-exp1.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 1 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
CP=build/exp1
java -cp $CP exp1.RmiChatServer > "$LOGS/exp1-server.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
sleep 2
OUT=$(java -cp $CP exp1.RmiChatClient Orion demo 2>&1); echo "$OUT"
echo; echo "################ SERVER LOG ################"
sleep 0.5; cat "$LOGS/exp1-server.log"
echo
if echo "$OUT" | grep -q "Remote object located" && echo "$OUT" | grep -q "Online users: \[Orion\]" && echo "$OUT" | grep -q "Server rejected the call"; then
    echo "[RESULT] PASS - remote methods ran in the server JVM and returned objects/exceptions"
else
    echo "[RESULT] FAIL"; exit 1
fi
