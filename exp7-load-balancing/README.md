# EXP 7 - Load Balancing (Round Robin and Least Connections)

## 1. What the experiment demonstrates
A **load balancer** on port 5200 sits in front of three chat servers (5201-5203). Clients only know the balancer. It
spreads requests with **Round Robin** or **Least Connections**, and routes around a server that dies.

## 2. Architecture
```
 clients --> LoadBalancer (5200) --+--> Server 1 (5201, slow)
                                   +--> Server 2 (5202, fast)
                                   +--> Server 3 (5203, medium)
```
In the one-JVM demo the three servers share one `ChatState` (like stateless servers over one database), so any server can
answer any request. Separate backend processes (`BackendServerMain`) each have their **own** state, which is documented
in the usage notes below.

## 3. Required concepts
- **Load balancing**: spread work over several servers for throughput and availability.
- **Round Robin**: next server in turn (`AtomicInteger` counter, `Math.floorMod`). Fair when requests and servers are alike.
- **Least Connections**: choose the server with the fewest active requests (per-backend counters, selection + increment synchronized). Better when requests differ in duration or servers differ in speed.
- **Health check / failover**: a failed connect marks the backend down; the request is retried on another server.
- **Single point of failure**: the balancer itself (not solved here).

## 4. Folder structure
```
exp7-load-balancing/src/exp7/
  LoadBalancer.java       Strategy enum, choose(), proxying, failure detection
  BackendServer.java      chat server with a configurable processing delay
  Exp7Demo.java           phases A-D (entry point)
  BackendServerMain.java  one backend as a process (entry point)
  LoadBalancerMain.java   balancer as a process (entry point)
  Exp7Client.java         client talking to the balancer (entry point)
```

## 5. How to compile
`./scripts/build.sh 7`

## 6-9. How to run
**Automatic:** `java -cp build/exp7 exp7.Exp7Demo` (or `./scripts/demo-exp7.sh`).

**Separate terminals** (backends first):

| Terminal | Command |
|---|---|
| 1 | `java -cp build/exp7 exp7.BackendServerMain "Server 1" 5201 200` |
| 2 | `java -cp build/exp7 exp7.BackendServerMain "Server 2" 5202 20` |
| 3 | `java -cp build/exp7 exp7.BackendServerMain "Server 3" 5203 80` |
| 4 | `java -cp build/exp7 exp7.LoadBalancerMain round_robin` (or `least_connections`) |
| 5 | `java -cp build/exp7 exp7.Exp7Client Orion demo` |

The last argument of a backend is the simulated work time in ms. Because each backend process has its own state, use the
client's `demo` mode (registrations) to watch the distribution; stop a backend with Ctrl+C to see rerouting.

## 10. What the expected output means
- **A. Round Robin**: 9 requests -> `3 / 3 / 3`, the balancer logs `request -> Server N` in rotation.
- **B. Selection rule**: counters 20 / 8 / 13 -> Server 2 is chosen (fewest connections).
- **C. Concurrent**: 12 clients x 5 messages = 60 requests; the fast Server 2 handles clearly more than slow Server 1
  (a run gave 10 / 38 / 12; numbers vary). All 60 messages are stored.
- **D. Failure**: Server 3 stopped; 6 of 6 requests are answered by Servers 1 and 2.

## 11. How to demonstrate the event
Phase D stops a real server socket; the balancer's connect fails, it marks the backend down and tries the next one.

## 12. Expected result
Even split in Round Robin, load-aware split in Least Connections, no request lost on failure. Ends with `[RESULT] PASS`.

## 13. Viva explanation
"The balancer accepts the client connection and forwards each request to a backend. Round Robin rotates through the
backends with an atomic counter. Least Connections counts the active requests per backend and picks the smallest, so the
fast server that finishes quickly receives more. If a backend refuses connections it is skipped."

## 14. Important viva questions
- *Round Robin vs Least Connections?* Rotation regardless of load vs choice by current load.
- *When is Round Robin bad?* Unequal servers or very different request lengths.
- *Why must the counters be thread-safe?* Many clients are routed at the same time.
- *Why share state between backends?* Otherwise a user registered on one server is unknown on another (sticky sessions or a shared store are needed).
- *What is the weakness of one balancer?* It is a single point of failure; use two balancers or DNS/virtual IP.
