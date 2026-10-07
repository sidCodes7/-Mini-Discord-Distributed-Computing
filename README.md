# MINI DISCORD - DISTRIBUTED COMPUTING LAB

A small Discord-like chat system, written in plain Java, used to demonstrate eight distributed-computing experiments (0-7).
Every experiment is a runnable program with its own README, demo and diagram. Shared chat code lives in `common/`.

## Purpose
Learn and demonstrate: distributed system basics, RMI, multithreading, clock synchronization, fault tolerance, data
consistency, leader election and load balancing - all on the **same** application, so each experiment builds on the previous one.

## Architecture (overview)
```
 clients --TCP--> [Load Balancer 5200] --> [chat servers 5201-5203]        (exp 7)
 clients --TCP--> [PRIMARY 5001] <==replication/heartbeat==> [BACKUP 5002]  (exp 4, 5)
 clients --RMI--> [RMI registry 1099 -> ChatService]                        (exp 1)
 election nodes 1..5 on ports 8001-8005                                     (exp 6)
 time server 7000, Berkeley nodes 7001-7004                                 (exp 3)
 all of them wrap the same ChatState (users, channels, messages)            (common/)
```
More detail: `docs/architecture.md`.

## Tech stack
Java 17 or newer (developed and tested with Java 21), standard library only (`java.net`, `java.util.concurrent`,
`java.rmi`). No Maven/Gradle, no external libraries. Build with `javac` through `scripts/build.sh`.

## Experiments
| Exp | Topic | Main idea | Folder |
|---|---|---|---|
| 0 | Distributed system | Client-server chat over TCP | `exp0-distributed-system/` |
| 1 | RPC / RMI | Remote method calls with stubs and a registry | `exp1-rmi/` |
| 2 | Multithreading | Thread pool, thread-safe state, race condition demo | `exp2-multithreading/` |
| 3 | Clock synchronization | Cristian and Berkeley | `exp3-clock-synchronization/` |
| 4 | Fault tolerance | Primary/backup, heartbeat, failover, snapshot recovery | `exp4-fault-tolerance/` |
| 5 | Data consistency | Synchronous vs delayed replication, stale reads | `exp5-data-consistency/` |
| 6 | Leader election | Bully and Ring | `exp6-leader-election/` |
| 7 | Load balancing | Round Robin and Least Connections | `exp7-load-balancing/` |

## How the experiments build on each other
Exp 0 creates the chat server. Exp 1 exposes it through RMI. Exp 2 makes it safe for many clients. Exp 3 gives nodes
comparable clocks (message timestamps). Exp 4 adds a backup so the chat survives a crash. Exp 5 shows what replication
costs in consistency. Exp 6 lets nodes choose a coordinator. Exp 7 spreads clients over several servers.

## Folder structure
```
Mini-Discord-Distributed-Computing/
  README.md  run-all.sh  run-all.bat
  common/src/minidiscord/common/   shared code (ChatState, protocol, TCP server/client, Log)
  exp0-distributed-system/ ... exp7-load-balancing/   each: README.md + src/
  scripts/   build.sh, build.bat, demo-exp0.sh ... demo-exp7.sh
  docs/      architecture.md, viva-notes.md, experiment-summary.md
```

## Prerequisites
JDK 17+ (`javac -version`, `java -version`) and, for the scripts, a Unix shell (Linux/macOS/Git Bash/WSL).

## Compile
```
./scripts/build.sh        # all experiments  -> build/exp0 ... build/exp7
./scripts/build.sh 4      # only experiment 4
```
(If the scripts are not executable: `chmod +x run-all.sh scripts/*.sh`.)

## Run
- Everything, unattended, with PASS/FAIL table: `./run-all.sh` (logs in `build/logs/`)
- One experiment: `./scripts/demo-exp<N>.sh`
- By hand in several terminals: see the "How to run" section of each experiment README.

## Ports
| Exp | Process | Port(s) |
|---|---|---|
| 0 | Exp0Server | 5000 |
| 1 | RMI registry | 1099 |
| 2 | ConcurrentChatServer | 5100 |
| 3 | Time server / Berkeley nodes | 7000 / 7001-7004 |
| 4 | PRIMARY / BACKUP | clients 5001 / 5002, replication 6001 / 6002 |
| 5 | none (in one JVM) | - |
| 6 | election nodes | 8001-8005 |
| 7 | load balancer / backends | 5200 / 5201-5203 |

## Troubleshooting
- `Address already in use`: an earlier run is still alive. Find it with `lsof -i :<port>` (or `ss -ltnp`) and stop it, or run `./run-all.sh` again (it cleans up its own processes).
- `javac: command not found`: install a JDK 17+ and put it on the PATH.
- Client says server unavailable: start the server first and check the port in the table.
- RMI cannot connect: the demos set `java.rmi.server.hostname=localhost`; do not run the RMI server behind a different hostname without changing it.
- Output line order differs: several threads/processes log at once (election log lines can interleave).
- `Picked up JAVA_TOOL_OPTIONS` lines come from your environment, not from this project.

## Honest limitations
- Exp 5 runs two separate `ChatState` replicas in **one JVM** (a replication simulation, no network).
- Exp 3 and Exp 6 demos run all nodes in one JVM over real sockets; separate-process mains also exist.
- Exp 7 demo backends share one `ChatState`; separate backend processes each have their own.
- `run-all.bat` and `scripts/build.bat` were written but **not tested on Windows**; the project was tested on Linux.

## Viva summary
Distributed system = independent nodes cooperating by messages. RMI = remote calls through stub and registry.
Thread pool + thread-safe collections avoid race conditions. Cristian = server time + RTT/2; Berkeley = average of
differences. Fault tolerance = replication + heartbeat + failover. Strong consistency = slower but never stale; eventual =
faster but can be stale. Bully = highest alive id wins by challenge; Ring = message circulates. Round Robin = rotate; Least
Connections = pick the least busy. Details: `docs/viva-notes.md`.
