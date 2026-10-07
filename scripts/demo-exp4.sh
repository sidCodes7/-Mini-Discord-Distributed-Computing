#!/usr/bin/env bash
# Automated EXP 4 demo with REAL processes: primary + backup + clients, then kill -9 on the primary.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/build.sh" 4 > /dev/null || exit 1
CP=build/exp4
LOGS=build/logs
mkdir -p "$LOGS"
PIDS=""
cleanup() { for p in $PIDS; do kill "$p" 2>/dev/null; done; }
trap cleanup EXIT

echo "### Starting BACKUP (client port 5002, replication port 6002)"
java -cp $CP exp4.FaultTolerantNode BACKUP backup 5002 6002 6001 > "$LOGS/backup.log" 2>&1 < /dev/null &
BACKUP=$!; PIDS="$PIDS $BACKUP"
sleep 1
echo "### Starting PRIMARY (client port 5001, replication port 6001)"
java -cp $CP exp4.FaultTolerantNode PRIMARY primary 5001 6001 6002 > "$LOGS/primary.log" 2>&1 < /dev/null &
PRIMARY=$!; PIDS="$PIDS $PRIMARY"
sleep 2

echo; echo "### STEP 1-2: client sends messages to the PRIMARY (each one is replicated to the BACKUP)"
java -cp $CP exp4.Exp4Client before; RC1=$?
sleep 1.5

echo; echo "### STEP 3: PRIMARY crashes (kill -9, no goodbye)"
kill -9 "$PRIMARY" 2> /dev/null; wait "$PRIMARY" 2> /dev/null

echo; echo "### STEP 4-6: client sends again; the BACKUP must detect the failure and take over"
java -cp $CP exp4.Exp4Client after; RC2=$?

echo; echo "### RECOVERY: old PRIMARY restarts as a standby and receives a snapshot from the new active node"
java -cp $CP exp4.FaultTolerantNode PRIMARY backup 5001 6001 6002 > "$LOGS/primary-restarted.log" 2>&1 < /dev/null &
RESTARTED=$!; PIDS="$PIDS $RESTARTED"
sleep 4
cleanup; sleep 0.5

echo; echo "################ BACKUP LOG (build/logs/backup.log) ################"
grep -v '^\[BACKUP\] Primary heartbeat received' "$LOGS/backup.log" | grep -v 'Heartbeat sent' | head -60
echo; echo "################ PRIMARY LOG (build/logs/primary.log) ################"
grep -v 'Heartbeat sent' "$LOGS/primary.log" | head -40
echo; echo "################ RESTARTED PRIMARY LOG ################"
grep -v 'heartbeat received' "$LOGS/primary-restarted.log" | head -20

RECOVERED=0
grep -q "Snapshot loaded: 2 users, 3 messages, last message id 3" "$LOGS/primary-restarted.log" && RECOVERED=1
echo
if [ $RC1 -eq 0 ] && [ $RC2 -eq 0 ] && [ $RECOVERED -eq 1 ]; then
    echo "[RESULT] PASS - failover kept the chat running and the restarted node recovered the full state"
else
    echo "[RESULT] FAIL (before=$RC1 after=$RC2 recovered=$RECOVERED)"; exit 1
fi
