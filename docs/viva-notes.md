# Viva Notes

## Definitions
- **Distributed system**: independent computers/processes that cooperate only by passing messages and appear as one system.
- **Node**: one running process/machine in the system. **Client/Server**: requester / provider.
- **RPC**: calling a procedure on another machine like a local call. **RMI**: Java's object-based RPC (stub, registry, serialization).
- **Stub**: client-side proxy that marshals arguments. **Registry**: name -> remote object directory.
- **Thread**: path of execution inside a process (shares memory). **Concurrency**: many tasks in progress; **parallelism**: truly simultaneous.
- **Race condition**: wrong result because of unsynchronized access to shared data. **Thread safety**: correct under concurrent use (atomics, concurrent collections, locks).
- **Clock drift**: clocks run at different rates. **Cristian**: `Ts + RTT/2`. **Berkeley**: average of all clocks, send adjustments. **Logical clock**: orders events (Lamport), not real time.
- **Fault / failure / fault tolerance**: cause / visible outage / keeping service despite faults. **Heartbeat**: periodic "I am alive". **Failover**: switch to standby. **Redundancy / replication**: extra copies.
- **Consistency**: replicas agree. **Strong**: reads see the latest write. **Eventual**: replicas converge later. **Stale read**: old data.
- **Leader election**: choosing one coordinator. **Bully**: highest alive id wins via ELECTION/OK/COORDINATOR. **Ring**: message circulates and collects ids.
- **Load balancer**: distributes requests. **Round Robin**: rotation. **Least Connections**: fewest active requests.
- **Single point of failure**: component whose failure stops everything. **Snapshot**: full copy of state.

## What did we implement?
- **Exp 0**: A TCP chat server holding all state in `ChatState` and clients that send text commands; threads serve clients concurrently.
- **Exp 1**: The same operations as an RMI remote object (`ChatService`), registry on 1099; output shows two different JVM pids to prove remote execution.
- **Exp 2**: A fixed thread-pool server with named workers; 8 clients x 25 messages stored without loss or duplicate ids; a race demo contrasting `int`/`ArrayList` with `AtomicLong`/`ConcurrentLinkedQueue`.
- **Exp 3**: Simulated drifting clocks; Cristian (adjustment = Ts + RTT/2 - t1) and Berkeley (average of differences, outliers ignored, ADJUST messages) over real sockets; verified by a small remaining difference.
- **Exp 4**: Primary and backup processes with replication, 1 s heartbeats, 3 s timeout, automatic takeover, client failover and snapshot recovery of the restarted node (tested with `kill -9`).
- **Exp 5**: Two replicas; synchronous vs delayed replication; `compareState` shows CONSISTENT/INCONSISTENT; lost update on source crash; catch-up by last message id.
- **Exp 6**: Five election nodes on ports 8001-8005; Bully and Ring elections after the coordinator (Node 5) crashes; both elect Node 4.
- **Exp 7**: A load balancer with Round Robin (3/3/3) and Least Connections (picks the least loaded; faster server gets more), plus rerouting when a server dies.

## Quick answers
- Why a thread pool? Bounded, reusable threads. Why `AtomicLong`? Lock-free unique ids.
- Cristian weakness? Asymmetric delay, single time server. Berkeley advantage? No accurate clock needed.
- Failure detection is only a timeout guess: slow and dead look alike.
- Strong consistency costs latency/availability; eventual consistency costs freshness (CAP).
- Bully cost O(n^2) worst case, Ring about 2n messages.
- Round Robin ignores load; Least Connections needs counters.
