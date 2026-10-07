# EXP 0 - Distributed System (Mini Discord base)

## 1. What the experiment demonstrates
The base architecture of the whole project: a **chat server** and several **chat clients** that run as
**separate processes** and communicate only through the network (TCP sockets). The server owns all
state (users, channels, channel membership, messages, message ids, timestamps). Clients own nothing.

## 2. Architecture
```
 Client (Orion)      Client (Sid)
      |                   |
      |  TCP, text lines  |
      +---------+---------+
                |
                v
        Chat Server (port 5000)
                |
                +---- #general
                +---- #coding
                +---- #random
                |
                +---- ChatState: users, members, message queues, id counter
```

## 3. Required concepts
- **Distributed system**: independent computers/processes cooperating by message passing, no shared memory.
- **Node, client, server**: every running process is a node; the server provides the service, clients request it.
- **Protocol**: both sides agree on a text format (`REGISTER`, `JOIN`, `SEND`, `GET`, ...), see `common/src/minidiscord/common/ChatProtocol.java`.
- **Thread per connection**: the server serves every client on its own thread (`Executors.newCachedThreadPool`).
- **Server-side state**: `ChatState` uses `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `CopyOnWriteArraySet`, `AtomicLong`.

## 4. Folder structure
```
exp0-distributed-system/
  README.md
  src/exp0/Exp0Server.java    server process (entry point)
  src/exp0/Exp0Client.java    client process (entry point)
common/src/minidiscord/common/  Message, User, Channel, ChatState, ChatProtocol,
                                TcpChatServer, ChatConnection, ConsoleClient, Log
```

## 5. How to compile
From the project root: `./scripts/build.sh 0`  (output in `build/exp0`)

## 6-9. How to run (terminals and exact commands)
Start the **server first**, then the clients. All commands are run from the project root.

| Terminal | Purpose | Command |
|---|---|---|
| 1 | server (port 5000) | `java -cp build/exp0 exp0.Exp0Server` |
| 2 | client Orion | `java -cp build/exp0 exp0.Exp0Client Orion demo` |
| 3 | client Sid | `java -cp build/exp0 exp0.Exp0Client Sid demo` |

Type commands yourself instead of the scripted demo: `java -cp build/exp0 exp0.Exp0Client Orion interactive`
and enter e.g. `REGISTER Orion`, `JOIN Orion #general`, `SEND Orion #general hello`, `GET #general`, `USERS`.

One-command automatic version: `./scripts/demo-exp0.sh`

## 10. What the expected output means
```
[CLIENT Orion] > REGISTER Orion          client sends a request line
[CLIENT Orion] OK Registered Orion       server answered OK (or "ERR reason")
[CLIENT Orion] > GET #general
[CLIENT Orion] OK 1                      the server will now send 1 message
[CLIENT Orion]    #1 [..] Orion in #general: Hello from Orion
[CLIENT Orion] OK Orion,Sid              USERS: everybody who is online
```
Server terminal: one line per request, e.g. `[SERVER] /127.0.0.1:60768 sent 'JOIN Orion #general' -> OK Orion joined #general`.

## 11. How to demonstrate the event
Run Orion, then Sid: Sid's `GET #general` returns Orion's message too, which proves the **state lives on the server**
and is shared. The "Error handling" part of the demo shows duplicate registration, unknown channel and empty message
being rejected without crashing the server.

## 12. Expected result
Both clients get `OK` answers; Sid sees `OK 2` messages in `#general` (Orion's and his own) and `USERS` returns `Orion,Sid`;
the three error requests answer `ERR ...`. `./scripts/demo-exp0.sh` ends with `[RESULT] PASS`.

## 13. Viva explanation
"We built the base of Mini Discord as a client-server distributed system. The server is one process that keeps users,
channels and messages. Clients are other processes that connect over TCP and send text commands. Because the server uses
thread-safe collections, many clients can use it at once. Every later experiment adds one distributed-computing idea
to this same system."

## 14. Important viva questions
- *What makes this a distributed system?* Separate processes, no shared memory, cooperation only through messages.
- *Where is the state stored?* On the server, in `ChatState`; clients are stateless.
- *Why a thread per client?* Otherwise one slow client would block all others.
- *What is a protocol?* The agreed format and meaning of messages between client and server.
- *What is the weakness of this design?* The single server is a single point of failure (solved in Exp 4) and one server may be overloaded (Exp 7).
