# EXP 3 - Clock Synchronization

## 1. What the experiment demonstrates
Every node has its own clock and the clocks **drift** apart. This experiment makes drift visible and then fixes it with two
algorithms that really calculate and apply adjustments: **Cristian's algorithm** (one time server) and the **Berkeley
algorithm** (a coordinator averages the group). It ends by stamping a Mini Discord message with the synchronized clock.

## 2. Architecture
```
 Cristian:                                  Berkeley:
 Client --TIME--> Time Server (7000)        Coordinator --TIME--> Node 1..4 (7001-7004)
 Client <--Ts---                            Coordinator --ADJUST delta--> each node
```
A node clock is `SimulatedClock` = real system clock + configurable offset (the drift, e.g. +5000 ms).
`ClockServer` simulates network delay by waiting before reading the clock and before replying.

## 3. Required concepts
- **Clock drift / skew**: clocks run at slightly different speeds or start different, so the same instant shows different times.
- **Physical clocks**: show real time (what this experiment synchronizes). **Logical clocks** (Lamport, vector) only order events, they do not show real time (theory only, not implemented here).
- **Cristian**: `t0` = send time, `Ts` = server time, `t1` = receive time, `RTT = t1 - t0`; best estimate of server now = `Ts + RTT/2`; `adjustment = (Ts + RTT/2) - t1`. Assumes the delay is symmetric.
- **Berkeley**: no accurate source. Coordinator polls all clocks, computes each difference, averages them (itself = 0, outliers > 60 s ignored) and sends each node `average - its difference`. Nodes are told a correction, not a time.

## 4. Folder structure
```
exp3-clock-synchronization/src/exp3/
  SimulatedClock.java       clock with offset, adjust()
  ClockServer.java          TCP node answering TIME and ADJUST (time server and Berkeley node)
  CristianClient.java       Cristian algorithm
  BerkeleyCoordinator.java  Berkeley algorithm
  Exp3Demo.java             all four parts in one run (entry point)
  TimeServerMain, CristianClientMain, ClockNodeMain, BerkeleyCoordinatorMain   separate processes (entry points)
```

## 5. How to compile
`./scripts/build.sh 3`

## 6-9. How to run
**Automatic:** `java -cp build/exp3 exp3.Exp3Demo` (or `./scripts/demo-exp3.sh`).

**Cristian in separate terminals** (port 7000):
| Terminal | Command |
|---|---|
| 1 (first) | `java -cp build/exp3 exp3.TimeServerMain 0 100 7000` |
| 2 | `java -cp build/exp3 exp3.CristianClientMain "Node A" 5000 7000` |
| 3 | `java -cp build/exp3 exp3.CristianClientMain "Node B" -3000 7000` |

**Berkeley in separate terminals** (ports 7001-7004):
| Terminal | Command |
|---|---|
| 1-4 (first) | `java -cp build/exp3 exp3.ClockNodeMain Node1 1000 7001`, then `Node2 3000 7002`, `Node3 5000 7003`, `Node4 7000 7004` |
| 5 | `java -cp build/exp3 exp3.BerkeleyCoordinatorMain 1500 4` |

## 10. What the expected output means
```
[CLOCK] Node A local time: 09:57:29.103   (offset +5000 ms)       drift made visible
[CLOCK Node A] CRISTIAN'S ALGORITHM
[CLOCK Node A] Client local time (t0) : 09:57:29.138
[CLOCK Node A] Server time (Ts)       : 09:57:24.260
[CLOCK Node A] Round trip time        : 243 ms
[CLOCK Node A] Estimated network delay: 121 ms (RTT / 2)
[CLOCK Node A] Calculated adjustment  : -5000 ms (Ts + delay - t1)
[CLOCK Node A] Corrected client time  : 09:57:24.385
[COORDINATOR] BERKELEY ALGORITHM
[COORDINATOR] Node 1 -> 09:57:29.923   RTT 81 ms, difference to coordinator +3500 ms
[COORDINATOR] Step 2: average difference over 5 clocks = +1800 ms
[COORDINATOR] Node 1 original ...  difference +3500 ms  adjustment -1700 ms  updated ...
[COORDINATOR] Largest remaining difference to the coordinator: 1 ms
```
Times differ in every run. The adjustment of Node A (-5000 ms) is its drift removed.

## 11. How to demonstrate the event
Part A prints three clocks with +5000, -3000, +2000 ms offsets at the same instant. After B and C the demo checks the
clocks again (`[CHECK] ... differs from the time server by 0 ms`, `Largest remaining difference ... 1 ms`).
The offsets inside `SimulatedClock` really change, so a second synchronization would find an adjustment of about 0.

## 12. Expected result
After Cristian every client is within a few ms of the time server; after Berkeley all nodes are within a few ms of the
coordinator's new clock; the chat message gets the synchronized timestamp. `Exp3Demo` ends with `[RESULT] PASS`
(limit 40 ms).

## 13. Viva explanation
"Each node's clock is the real clock plus a drift offset. Cristian asks a time server for its time, measures the round
trip, assumes the reply took half of it and sets the clock to server time plus that delay. Berkeley has no reference
clock: the coordinator collects every clock, averages the differences and sends each node the amount to add."

## 14. Important viva questions
- *Why can't we just copy the server's time?* The reply travelled for some time; Cristian adds RTT/2.
- *Weakness of Cristian?* Single time server; error up to RTT/2 if the delay is asymmetric.
- *Cristian vs Berkeley?* Cristian needs an accurate external source, Berkeley synchronizes to the group average.
- *Why does Berkeley send adjustments, not the time?* The message itself takes time; a relative correction is not affected by it.
- *Why ignore outliers in Berkeley?* A faulty clock would ruin the average.
- *Physical vs logical clocks?* Real time vs ordering of events (Lamport/vector).
