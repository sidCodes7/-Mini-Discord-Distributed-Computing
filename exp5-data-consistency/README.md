# EXP 5 - Data Consistency and Replication

## 1. What the experiment demonstrates
Two replicas of the Mini Discord state are kept in step in two ways. **Synchronous** replication gives the same data
everywhere before the client gets an answer. **Delayed (asynchronous)** replication is faster but a replica can be
**stale**, and if the source crashes before the delayed update arrives, an update is lost. A checker prints whether
the replicas are CONSISTENT or INCONSISTENT and a catch-up repairs the stale replica.

## 2. Architecture
```
 write --> ReplicaNode A (ChatState) --ReplicationLink--> ReplicaNode B (ChatState)
                                          SYNCHRONOUS : apply on B, then reply
                                          DELAYED     : queue, apply on B later
 ConsistencyChecker.compareState(A, B)  ->  CONSISTENT / INCONSISTENT
```
No network ports: this experiment runs inside one JVM with two **separate** `ChatState` objects (stated honestly: it is a
replication simulation, not two processes).

## 3. Required concepts
- **Consistency**: every replica returns the same data.
- **Strong consistency**: after a write completes, every read sees it (synchronous replication).
- **Eventual consistency**: replicas converge after some time if there are no new writes (delayed replication).
- **Stale read**: reading an older value from a replica that has not received the update yet.
- **Replication lag**: the delay between the write on A and its application on B.
- **Catch-up / anti-entropy**: B asks for everything after its `lastMessageId`.
- **Trade-off**: strong = slower writes; eventual = faster but stale reads or lost updates.

## 4. Folder structure
```
exp5-data-consistency/src/exp5/
  ReplicaNode.java         a chat replica with its own ChatState
  ReplicationLink.java     SYNCHRONOUS or DELAYED forwarding of operation lines
  ConsistencyChecker.java  compareState(...)
  Exp5Demo.java            three scenarios (entry point)
```

## 5. How to compile
`./scripts/build.sh 5`

## 6-9. How to run
One terminal, no server to start first: `java -cp build/exp5 exp5.Exp5Demo` (or `./scripts/demo-exp5.sh`).

## 10. What the expected output means
- **Scenario 1 (synchronous)**: write on A, B has it immediately - `CONSISTENT`.
- **Scenario 2 (delayed)**: write on A, B read right away is missing the message - `INCONSISTENT` (backup missing id 2).
  If A crashes before the delayed update is delivered, that update is **lost** (1 update lost).
- **Scenario 3 (catch-up)**: B requests messages after its last id, applies them - `CONSISTENT` again.

## 11. How to demonstrate the event
The demo itself performs the inconsistency: it reads from the lagging replica and prints the comparison, then crashes the
source and shows the loss, then repairs by catch-up.

## 12. Expected result
CONSISTENT, then INCONSISTENT with a lost update, then CONSISTENT after catch-up. Ends with `[RESULT] PASS`.

## 13. Viva explanation
"Replication copies each operation to a second node. If we wait for the copy before replying, replicas always agree
(strong consistency) but writes are slower. If we reply first and copy later, writes are fast but a read from the replica
can be stale and a crash can lose the update. Eventual consistency accepts that and repairs it with a catch-up based on the
last message id. `applyOp` is idempotent, so repeating an operation does no harm."

## 14. Important viva questions
- *Strong vs eventual consistency?* Always-latest read vs agreement after some time.
- *Why not always synchronous?* Latency and availability: a slow or dead replica would block every write.
- *What is a stale read?* Old data returned by a replica that is behind.
- *CAP theorem?* During a network partition choose consistency or availability, not both (theory only).
- *Why idempotent operations?* A retried or duplicated update must not create duplicate messages.
- *How does the replica repair itself?* It asks for operations after its last known message id.
