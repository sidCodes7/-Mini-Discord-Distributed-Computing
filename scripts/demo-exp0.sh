#!/usr/bin/env bash
# Automated demo for EXP 0. Usage: ./scripts/demo-exp0.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 0 > /dev/null || exit 1
LOGS=build/logs
mkdir -p "$LOGS"
CP=build/exp0
java -cp $CP exp0.Exp0Server > "$LOGS/exp0-server.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
sleep 1.5
echo "### Client Orion"
OUT1=$(java -cp $CP exp0.Exp0Client Orion demo 2>&1); echo "$OUT1"
echo; echo "### Client Sid"
OUT2=$(java -cp $CP exp0.Exp0Client Sid demo 2>&1); echo "$OUT2"
echo; echo "################ SERVER LOG ################"
sleep 0.5; cat "$LOGS/exp0-server.log"
echo
if echo "$OUT1" | grep -q "OK Message 1 stored" && echo "$OUT2" | grep -q "OK 2" && echo "$OUT2" | grep -q "OK Orion,Sid"; then
    echo "[RESULT] PASS - two clients used one server, history and online users are shared"
else
    echo "[RESULT] FAIL"; exit 1
fi
