# EXP 6 - Leader Election (Bully and Ring)

## 1. What the experiment demonstrates
Five Mini Discord nodes need one **coordinator**. When the coordinator (Node 5) crashes, the others elect a new one with
two classic algorithms, both over real TCP sockets: **Bully** and **Ring**. Both elect Node 4.

## 2. Architecture
```
 Node 1 (8001)  Node 2 (8002)  Node 3 (8003)  Node 4 (8004)  Node 5 (8005, coordinator)
 Bully: higher ids are asked   ELECTION --> OK <-- COORDINATOR
 Ring : 1 -> 2 -> 3 -> 4 -> 5 -> 1   RING_ELECTION collects ids, RING_COORDINATOR announces the highest alive id
```
Node id N listens on port 8000 + N.

## 3. Required concepts
- **Coordinator / leader**: the node in charge; others follow it.
- **Bully**: a node that detects failure sends `ELECTION` to all higher ids. Any live higher node answers `OK` and takes over the election. A node that gets no `OK` declares itself winner and sends `COORDINATOR` to all. Highest live id always wins.
- **Ring**: nodes form a logical ring; the election message travels around collecting ids; dead nodes are skipped; the highest id is announced with `RING_COORDINATOR`.
- **Failure detection**: a connection to the coordinator fails/times out.

## 4. Folder structure
```
exp6-leader-election/src/exp6/
  ElectionNode.java      one node: server socket, Bully and Ring logic
  Exp6Demo.java          automatic demo: both | bully | ring (entry point)
  ElectionNodeMain.java  one node as its own process (entry point)
```

## 5. How to compile
`./scripts/build.sh 6`

## 6-9. How to run
**Automatic:** `java -cp build/exp6 exp6.Exp6Demo both` (or `bully` / `ring`; or `./scripts/demo-exp6.sh`).

**Separate terminals** (start all five first, node 5 is the initial coordinator):

| Terminal | Command |
|---|---|
| 1-5 | `java -cp build/exp6 exp6.ElectionNodeMain 1` ... `exp6.ElectionNodeMain 5` |
| 5 | type `crash` (Node 5 stops) |
| 2 | type `bully` (or `ring`) |
| any | type `status` to see the new coordinator |

## 10. What the expected output means
Bully run: Node 5 crashes; Node 2 notices; it sends ELECTION to 3, 4, 5; 3 and 4 answer OK; Node 4 sends ELECTION to 5
(no answer) and announces itself `COORDINATOR`. Ring run: the visited nodes are `[2, 3, 4, 1]` (Node 5 skipped), highest is 4.
Log lines may interleave because forwarding is asynchronous.

## 11. How to demonstrate the failure
The coordinator really closes its server socket (`crash`), so connections to it are refused - that is the detected failure.

## 12. Expected result
Every alive node reports `coordinator = Node 4`. `Exp6Demo` ends with `[RESULT] PASS`.

## 13. Viva explanation
"Every node has an id. In Bully the node that notices the failure challenges all higher ids; if somebody higher answers, it
takes over, otherwise the node wins and tells everyone, so the highest alive id becomes coordinator. In Ring the election
message goes around the ring collecting ids and the highest one is announced. Both give Node 4 after Node 5 crashed."

## 14. Important viva questions
- *Why is it called Bully?* The strongest (highest id) node always overrides the others.
- *Bully vs Ring message cost?* Bully worst case O(n^2) messages, Ring about 2n.
- *What if two nodes start an election at once?* Both converge to the same highest id, so the result is the same.
- *What does OK mean in Bully?* "A higher node is alive, stop your election."
- *Weakness?* Needs reliable failure detection; a slow node may be falsely declared dead.
