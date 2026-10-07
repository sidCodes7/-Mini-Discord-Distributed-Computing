#!/usr/bin/env bash
# Build everything and run the automated demo of every experiment (0-7), one after the other.
# Full output of each demo is saved in build/logs/expN-demo.log
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
chmod +x scripts/*.sh 2> /dev/null

echo "Building all experiments ..."
./scripts/build.sh || { echo "Build failed"; exit 1; }
mkdir -p build/logs

NAMES=("Distributed system" "RMI" "Multithreading" "Clock synchronization" "Fault tolerance" "Data consistency" "Bully + Ring election" "Load balancing")
FAILED=0
echo
echo "EXP  RESULT  TIME   EXPERIMENT"
echo "---  ------  -----  ------------------------"
for n in 0 1 2 3 4 5 6 7; do
    START=$(date +%s)
    ./scripts/demo-exp$n.sh > "build/logs/exp$n-demo.log" 2>&1
    RC=$?
    ELAPSED=$(( $(date +%s) - START ))
    if [ $RC -eq 0 ] && grep -q "PASS" "build/logs/exp$n-demo.log"; then RESULT="PASS"; else RESULT="FAIL"; FAILED=1; fi
    printf "%-3s  %-6s  %3ss   %s\n" "$n" "$RESULT" "$ELAPSED" "${NAMES[$n]}"
done
echo
if [ $FAILED -eq 0 ]; then echo "All 8 experiments passed. Logs: build/logs/"; else echo "Some experiments failed - read the logs in build/logs/"; fi
exit $FAILED
