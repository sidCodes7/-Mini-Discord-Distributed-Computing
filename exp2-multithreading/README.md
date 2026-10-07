# EXP 2 - Multithreading and Concurrency

## 1. What the experiment demonstrates
One chat server serves **many clients at the same time** using a pool of worker threads, and the shared chat state stays
correct. A second program shows what goes wrong **without** thread-safe structures (race condition).

## 2. Architecture
```
 Client 1 --\
 Client 2 ---+--> ConcurrentChatServer (port 5100)
 ...        /          |
 Client 8 -/           +-- Worker-1 ... Worker-8   (ExecutorService, fixed thread pool)
                       |
                       +-- ONE shared ChatState
                             AtomicLong              unique message ids
                             ConcurrentHashMap       users, channels
                             ConcurrentLinkedQueue   messages
                             CopyOnWriteArraySet     channel members
```

## 3. Required concepts
- **Thread / Runnable / Callable**: a thread is an independent path of execution; a pool (`ExecutorService`) reuses a fixed number of them.
- **Concurrency vs parallelism**: concurrency = many tasks in progress at once; parallelism = tasks literally running at the same moment on several cores.
- **Shared state**: all workers touch the same `ChatState`.
- **Race condition**: result depends on thread timing, e.g. two threads doing `counter++` lose updates.
- **Thread safety**: `AtomicLong.incrementAndGet()` (one atomic step), `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `CopyOnWriteArraySet`.
- **CountDownLatch**: the demo uses it as a starting gun so all clients really start together.

## 4. Folder structure
```
exp2-multithreading/src/exp2/
  ConcurrentChatServer.java   server with named worker threads (entry point)
  ConcurrentChatClient.java   client for multi-terminal use (entry point)
  ConcurrencyDemo.java        8 client threads + verification (entry point)
  RaceConditionDemo.java      unsafe vs safe counter/list (entry point)
```

## 5. How to compile
`./scripts/build.sh 2`

## 6-9. How to run
**Automatic (one terminal):** `java -cp build/exp2 exp2.ConcurrencyDemo` and `java -cp build/exp2 exp2.RaceConditionDemo`
(or `./scripts/demo-exp2.sh`).

**Several terminals:**
| Terminal | Command |
|---|---|
| 1 (first) | `java -cp build/exp2 exp2.ConcurrentChatServer` |
| 2 | `java -cp build/exp2 exp2.ConcurrentChatClient Orion demo` |
| 3 | `java -cp build/exp2 exp2.ConcurrentChatClient Sid demo` |
| 4 | `java -cp build/exp2 exp2.ConcurrentChatClient Het demo` |

Start terminals 2-4 at almost the same moment. Port 5100.

## 10. What the expected output means
```
[Worker-7] Orion sending message     <- thread name shows WHICH worker handled the request
[Worker-4] Eli sending message          Several workers are active at the same time
[Worker-5] Het sending message
[CHECK] Messages sent by clients : 200
[CHECK] Messages stored on server: 200
[CHECK] Distinct message ids     : 200 (highest id 200)
[RESULT] PASS - no message lost, every id unique
```
`RaceConditionDemo` (numbers differ on every run):
```
[RACE] Expected 1000000 ids, plain int counter = 994047  (lost 5953)
[SAFE] Expected 1000000 ids, AtomicLong        = 1000000  (lost 0)
[RACE] Expected 200000 messages, ArrayList holds 190288  (lost 9712, exceptions 0)
[SAFE] Expected 200000 messages, ConcurrentLinkedQueue holds 200000  (lost 0)
```

## 11. How to demonstrate the event
Run the race demo first ("this is the problem"), then the concurrency demo ("this is why Mini Discord does not have it").
The client threads really operate on Mini Discord: they send messages through real TCP connections, and one client also
reads (`GET`) while the others write.

## 12. Expected result
200 messages stored, ids 1..200 all distinct; the unsafe counter/list lose updates, the safe versions lose nothing.

## 13. Viva explanation
"Every client connection is handled by a worker thread from a fixed pool. Because all workers share one `ChatState`, it is
built from thread-safe classes: `AtomicLong` gives every message a unique id without a lock, `ConcurrentHashMap` stores
users and channels, `ConcurrentLinkedQueue` stores messages. The race demo shows that plain `int` and `ArrayList` lose data
under the same load."

## 14. Important viva questions
- *Thread vs process?* Threads share memory inside one process; processes do not.
- *Concurrency vs parallelism?* Interleaving vs literally simultaneous execution.
- *What is a race condition?* Outcome depends on unlucky timing of unsynchronized access to shared data.
- *Why is `count++` not atomic?* It is read, add, write: three steps another thread can interleave with.
- *Why AtomicLong instead of `synchronized`?* Lock-free hardware compare-and-set, simpler and faster here.
- *Why a thread pool?* Creating a thread per request is expensive and unbounded; a pool limits and reuses.
