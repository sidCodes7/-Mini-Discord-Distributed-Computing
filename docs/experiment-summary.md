# Experiment Summary

Each entry: Aim, Problem, Architecture, Implementation, Key classes, Key concepts, How it works, Expected output, Outcome, Conclusion.

## Exp 0 - Distributed System
- **Aim**: build the client-server base of Mini Discord.
- **Problem**: users on different processes must share channels and messages.
- **Architecture**: many clients -> one TCP server (5000) -> `ChatState`.
- **Implementation**: line protocol over sockets, thread per connection.
- **Key classes**: `Exp0Server`, `Exp0Client`, `ChatState`, `ChatProtocol`, `TcpChatServer`.
- **Key concepts**: node, protocol, server-side state, message passing.
- **How it works**: client sends `SEND Orion #general hi`; server updates state and answers `OK`.
- **Expected output**: `OK` answers, second client sees first client's message, errors as `ERR`.
- **Outcome**: shared state across processes (demo `[RESULT] PASS`).
- **Conclusion**: a distributed system needs only agreed messages; the single server is a weak point.

## Exp 1 - RMI
- **Aim**: call chat operations remotely like local methods.
- **Problem**: hide sockets and parsing from the client.
- **Architecture**: client stub -> registry 1099 -> `ChatServiceImpl`.
- **Implementation**: `Remote` interface, `UnicastRemoteObject`, `Registry.rebind/lookup`, `Serializable Message`.
- **Key classes**: `ChatService`, `ChatServiceImpl`, `RmiChatServer`, `RmiChatClient`.
- **Key concepts**: stub, marshalling, RemoteException.
- **How it works**: lookup -> stub -> method call executes in server JVM.
- **Expected output**: different pids for client and server; stub proxy class.
- **Outcome**: remote execution verified; server exceptions reach the client.
- **Conclusion**: RMI gives location transparency but failures must still be handled.

## Exp 2 - Multithreading
- **Aim**: serve many clients at once safely.
- **Problem**: concurrent writers corrupt shared data.
- **Architecture**: fixed worker pool over one shared `ChatState`.
- **Implementation**: `ExecutorService`, `AtomicLong`, concurrent collections, `CountDownLatch` in the demo.
- **Key classes**: `ConcurrentChatServer`, `ConcurrencyDemo`, `RaceConditionDemo`.
- **Key concepts**: race condition, atomicity, thread safety.
- **How it works**: each request runs on a worker; ids come from `incrementAndGet`.
- **Expected output**: 200 sent = 200 stored, 200 distinct ids; unsafe counters lose updates.
- **Outcome**: PASS.
- **Conclusion**: shared state must use thread-safe structures.

## Exp 3 - Clock Synchronization
- **Aim**: bring drifting clocks together.
- **Problem**: nodes show different times.
- **Architecture**: time server 7000; Berkeley nodes 7001-7004.
- **Implementation**: `SimulatedClock` offsets; Cristian and Berkeley with real adjustments.
- **Key classes**: `SimulatedClock`, `ClockServer`, `CristianClient`, `BerkeleyCoordinator`, `Exp3Demo`.
- **Key concepts**: drift, RTT, symmetric delay, averaging.
- **How it works**: Cristian sets `t1 + (Ts + RTT/2 - t1)`; Berkeley sends each node `average - difference`.
- **Expected output**: adjustments such as -5000 ms; remaining difference of a few ms.
- **Outcome**: PASS (within 40 ms).
- **Conclusion**: sync accuracy is limited by network delay symmetry.

## Exp 4 - Fault Tolerance
- **Aim**: survive a server crash.
- **Problem**: one server is a single point of failure.
- **Architecture**: PRIMARY 5001/6001 and BACKUP 5002/6002.
- **Implementation**: operation replication, 1 s heartbeat, 3 s timeout, promotion, client failover, snapshot recovery.
- **Key classes**: `FaultTolerantNode`, `FailoverClient`, `Exp4Client`.
- **Key concepts**: redundancy, heartbeat, failover, recovery.
- **How it works**: primary replicates; backup detects silence and takes over.
- **Expected output**: "Primary failure detected", "Failover complete", 3 messages after failover, "Snapshot loaded: 2 users, 3 messages".
- **Outcome**: PASS with real `kill -9`.
- **Conclusion**: redundancy plus detection keeps service up; split brain remains an open problem.

## Exp 5 - Data Consistency
- **Aim**: show the consistency/latency trade-off.
- **Problem**: replicas can disagree.
- **Architecture**: two `ReplicaNode`s joined by a `ReplicationLink` (in one JVM).
- **Implementation**: SYNCHRONOUS vs DELAYED link, `compareState`, catch-up by last id.
- **Key classes**: `ReplicaNode`, `ReplicationLink`, `ConsistencyChecker`, `Exp5Demo`.
- **Key concepts**: strong/eventual consistency, stale read, replication lag.
- **How it works**: sync applies before replying; delayed queues the update.
- **Expected output**: CONSISTENT, INCONSISTENT (missing id 2, 1 update lost on crash), CONSISTENT after catch-up.
- **Outcome**: PASS.
- **Conclusion**: speed and freshness trade off.

## Exp 6 - Leader Election
- **Aim**: choose a new coordinator after a crash.
- **Problem**: nodes must agree on one leader without a central authority.
- **Architecture**: five nodes, ports 8001-8005.
- **Implementation**: Bully (ELECTION/OK/COORDINATOR) and Ring (collect ids, announce highest).
- **Key classes**: `ElectionNode`, `Exp6Demo`, `ElectionNodeMain`.
- **Key concepts**: coordinator, id ordering, failure detection.
- **How it works**: see README; Node 5 is crashed first.
- **Expected output**: Node 4 elected by both; ring visited [2, 3, 4, 1].
- **Outcome**: PASS.
- **Conclusion**: both elect the highest alive id; Bully is faster, Ring uses fewer messages.

## Exp 7 - Load Balancing
- **Aim**: spread requests over servers.
- **Problem**: one server overloaded, others idle.
- **Architecture**: balancer 5200 -> backends 5201-5203.
- **Implementation**: Round Robin with `AtomicInteger`; Least Connections with per-backend counters; failure rerouting.
- **Key classes**: `LoadBalancer`, `BackendServer`, `Exp7Demo`.
- **Key concepts**: scheduling policy, health check, shared state.
- **How it works**: balancer picks a backend per request and proxies it.
- **Expected output**: 3/3/3; least loaded chosen; fast server gets most; 6 of 6 answered after a failure.
- **Outcome**: PASS.
- **Conclusion**: load-aware balancing beats rotation when servers differ; the balancer itself is a single point of failure.
