# EXP 4 - Fault Tolerance (Primary / Backup)

## 1. What the experiment demonstrates
Mini Discord keeps working when the server process **crashes**. A PRIMARY and a BACKUP node run as separate processes.
The primary replicates every change to the backup and sends heartbeats. When the primary is killed (`kill -9`), the
backup notices the missing heartbeats, **promotes itself** and the client continues without losing a message.
Afterwards the old primary restarts as a standby and receives a **snapshot** of the current state.

## 2. Architecture
```
            clients (FailoverClient: tries PRIMARY, then BACKUP)
                 |                              |
          port 5001                        port 5002
        +-----------+  replication/heartbeat +-----------+
        |  PRIMARY  | ---------------------> |  BACKUP   |
        | ChatState |   port 6002            | ChatState |
        +-----------+ <--------------------- +-----------+
                         heartbeat, port 6001
```

## 3. Required concepts
- **Fault / failure / fault tolerance**: a fault is the cause, a failure is the visible outage; fault tolerance = the service continues anyway.
- **Redundancy**: a second copy of server and data (the backup).
- **Replication**: each state-changing request is sent to the peer as an operation line (`MSG|id|ts|sender|channel|text`).
- **Heartbeat + timeout**: a node sends `HEARTBEAT` every 1 s; silence for more than 3 s (timeout) = failure suspected.
- **Failover**: the backup becomes the active server automatically.
- **Snapshot recovery**: a restarted node asks for the full state (`snapshotOps()`) and loads it (`reset()` + `applyOp`).
- **Single point of failure**: removed by having two nodes.

## 4. Folder structure
```
exp4-fault-tolerance/src/exp4/
  FaultTolerantNode.java  one node (primary or backup role), replication, heartbeat, failover (entry point)
  FailoverClient.java     client connection that switches to the next server when one is unreachable
  Exp4Client.java         scripted client: before | after | interactive (entry point)
common/ ChatState (applyOp, snapshotOps, reset), TcpChatServer
```

## 5. How to compile
`./scripts/build.sh 4`

## 6-9. How to run
Start the **backup first**. Ports: clients 5001/5002, replication 6001/6002.

| Terminal | Purpose | Command |
|---|---|---|
| 1 | BACKUP | `java -cp build/exp4 exp4.FaultTolerantNode BACKUP backup 5002 6002 6001` |
| 2 | PRIMARY | `java -cp build/exp4 exp4.FaultTolerantNode PRIMARY primary 5001 6001 6002` |
| 3 | client, before failure | `java -cp build/exp4 exp4.Exp4Client before` |
| 2 | **crash the primary**: type `FAIL` (or press Ctrl+C / `kill -9 <pid>`) | |
| 3 | client, after failure | `java -cp build/exp4 exp4.Exp4Client after` |
| 4 | restart old primary as standby | `java -cp build/exp4 exp4.FaultTolerantNode PRIMARY backup 5001 6001 6002` |

Node console commands: `STATUS`, `MESSAGES`, `FAIL`, `QUIT`.
One-command version (real processes, uses `kill -9`): `./scripts/demo-exp4.sh`.

## 10. What the expected output means
Backup log:
```
[BACKUP] Primary failure detected (no heartbeat for more than 3000 ms)
[FAULT TOLERANCE] Primary failed
[FAULT TOLERANCE] Backup taking over
[FAULT TOLERANCE] Failover complete
```
Client: `PRIMARY` is unreachable, the client switches to `BACKUP`, sends message 3 and `GET #general` returns 3 messages
(two replicated before the crash plus the new one). Restarted node:
`Snapshot loaded: 2 users, 3 messages, last message id 3`.

## 11. How to demonstrate the failure
The failure is a real process kill (`kill -9`): no goodbye message, the socket just disappears. The backup is not told;
it **detects** the failure only through missing heartbeats.

## 12. Expected result
No message lost, the chat continues on the backup, the recovered node has the same 3 messages.
`./scripts/demo-exp4.sh` ends with `[RESULT] PASS`.

## 13. Viva explanation
"We run a primary and a backup server. Every write is replicated to the backup and the nodes exchange heartbeats. If the
primary dies, the backup sees no heartbeat for 3 seconds, declares failure and becomes active. The client library tries the
next server automatically. When the old primary comes back it fetches a snapshot, so it joins as an up-to-date standby."

## 14. Important viva questions
- *Fault vs failure?* Fault = defect/cause; failure = the system's behaviour deviates because of it.
- *How is a crash detected?* Heartbeat timeout; a slow node and a dead node look the same, so a timeout is only a suspicion.
- *Why replicate before answering?* So the backup already has the data when the primary dies.
- *What is failover?* Switching service to the standby automatically.
- *What problem remains?* Split brain (both think they are primary) if only the link breaks; production systems use quorum/consensus. Not implemented here.
- *Why a snapshot for recovery?* The restarted node missed the operations that happened while it was down.
