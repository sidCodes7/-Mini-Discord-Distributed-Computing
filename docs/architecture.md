# Architecture

## Shared core (`common/src/minidiscord/common/`)
| Class | Role |
|---|---|
| `ChatState` | All chat data: users, online users, channels (`#general`, `#coding`, `#random`), members, messages, id counter. Thread-safe. `applyOp` applies a replicated operation (idempotent); `snapshotOps` / `reset` copy a whole state. |
| `Message`, `User`, `Channel` | Data classes. `Message` is `Serializable` (used by RMI) and has a wire format. |
| `ChatProtocol` | Parses one text request line (`REGISTER`, `LOGIN`, `LOGOUT`, `JOIN`, `LEAVE`, `SEND`, `GET`, `USERS`, `CHANNELS`, `PING`) and returns `OK ...` or `ERR ...`. |
| `TcpChatServer` | ServerSocket + ExecutorService, one task per connection, pluggable request handler; `stop()` closes client sockets to simulate a crash. |
| `ChatConnection`, `ConsoleClient` | Client side of the line protocol; scripted and interactive console. |
| `Log` | Uniform `[TAG] text` console output. |

## Wire protocol
One request per line, one answer per request: `OK <text>` / `ERR <reason>`. `GET` answers `OK <n>` followed by n message lines.
Replication uses operation lines: `REGISTER|`, `LOGIN|`, `LOGOUT|`, `JOIN|`, `LEAVE|`, `MSG|id|timestamp|sender|channel|urlencoded-text`.

## Per experiment
- **Exp 0**: `Exp0Server` = `TcpChatServer` + `ChatProtocol` + one `ChatState`; `Exp0Client` sends lines.
- **Exp 1**: `ChatService` (Remote) <- `ChatServiceImpl` (UnicastRemoteObject) wraps `ChatState`; registry on 1099.
- **Exp 2**: same server with a fixed, named worker pool; `RaceConditionDemo` shows `int`/`ArrayList` losing updates vs `AtomicLong`/`ConcurrentLinkedQueue`.
- **Exp 3**: `SimulatedClock` (system clock + offset); `ClockServer` answers `TIME` and `ADJUST`; `CristianClient`, `BerkeleyCoordinator`.
- **Exp 4**: `FaultTolerantNode` (role primary/backup) = chat server + replication listener + heartbeat sender/monitor; `FailoverClient` walks a server list.
- **Exp 5**: `ReplicaNode` x2, `ReplicationLink` (SYNCHRONOUS or DELAYED), `ConsistencyChecker`.
- **Exp 6**: `ElectionNode` listens on 8000+id and implements Bully and Ring messages.
- **Exp 7**: `LoadBalancer` accepts clients and proxies each request to a `BackendServer` chosen by strategy.

## Concurrency model
Server side: thread per connection from an `ExecutorService`. State: `ConcurrentHashMap`, `ConcurrentLinkedQueue`,
`CopyOnWriteArraySet`, `AtomicLong`. No global lock is needed except in the Least Connections selection (select + increment
must be one atomic step).

## Failure model
Crash failures only (a process stops). No malicious (Byzantine) behaviour, no network partitions handling, no persistence to disk.
