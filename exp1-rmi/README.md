# EXP 1 - RPC / Java RMI

## 1. What the experiment demonstrates
The same Mini Discord operations (`registerUser`, `joinChannel`, `sendMessage`, `getMessages`, `getOnlineUsers`)
are called as **ordinary Java method calls on a remote object**. The method body runs in the **server JVM**, not in the client.

**RPC** = the general idea: call a procedure on another machine as if it were local.
**RMI** = Java's object-oriented implementation of it (remote *objects*, stubs, serialization, registry).

## 2. Architecture
```
 Client JVM                         Server JVM
 +-----------+   lookup("MiniDiscordChat")   +----------------+
 | stub      | ----------------------------> | RMI Registry   |  port 1099
 | (proxy)   | <---------------------------- | name -> object |
 |           |                               +----------------+
 |           |   sendMessage(...)  (serialized over TCP)
 |           | ----------------------------> ChatServiceImpl -> ChatState
 |           | <---------------------------- returns Message (serialized)
 +-----------+
```

## 3. Required concepts
- **Remote interface** (`ChatService extends Remote`): the methods a client may call; each `throws RemoteException`.
- **Remote implementation** (`ChatServiceImpl extends UnicastRemoteObject`): exports the object so it can receive calls.
- **RMI registry** (`LocateRegistry.createRegistry(1099)`): a phone book mapping a name to a remote object.
- **bind / lookup**: server `registry.rebind("MiniDiscordChat", impl)`, client `registry.lookup("MiniDiscordChat")`.
- **Stub (proxy)**: what the client really holds (`$Proxy0`); it marshals arguments, sends them, waits for the result.
- **Serialization**: arguments and results (`String`, `Message`, `List`) must be `Serializable`; `Message` implements it.
- **RemoteException**: thrown when the network/server fails. Exceptions thrown by the server code (e.g. `ChatException`) travel back to the client too.

## 4. Folder structure
```
exp1-rmi/src/exp1/
  ChatService.java       remote interface
  ChatServiceImpl.java   remote implementation (wraps ChatState)
  RmiChatServer.java     creates registry, binds object (entry point)
  RmiChatClient.java     looks up and calls the object (entry point)
```

## 5. How to compile
`./scripts/build.sh 1`

## 6-9. How to run
| Terminal | Purpose | Command |
|---|---|---|
| 1 (first) | registry + server | `java -cp build/exp1 exp1.RmiChatServer` |
| 2 | client demo | `java -cp build/exp1 exp1.RmiChatClient Orion demo` |
| 3 (optional) | interactive client | `java -cp build/exp1 exp1.RmiChatClient Sid interactive` |

One-command version: `./scripts/demo-exp1.sh`. If port 1099 is busy: `RmiChatServer 1100` and `RmiChatClient Orion demo 1100`.

## 10. What the expected output means
Client:
```
[CLIENT] Connected to RMI Registry
[CLIENT] Remote object located
[CLIENT] The object I hold is a stub (proxy): true -> $Proxy0
[CLIENT] My JVM:     pid=457
[CLIENT] Server JVM: JVM pid=434, thread=RMI TCP Connection(2)-127.0.0.1   <- the methods run THERE
[CLIENT] Calling sendMessage(Orion, #general, ...)
[CLIENT] Server returned a serialized Message object: #1 [..] Orion in #general: Hello over RMI from Orion
```
Server:
```
[SERVER] RMI Registry started on port 1099
[SERVER] MiniDiscordChat bound
[SERVER] Remote sendMessage(Orion, #general, "Hello over RMI from Orion") received
```
Two different process ids (pid) prove the code ran in another JVM. The thread name `RMI TCP Connection` is an RMI worker on the server.

## 11. How to demonstrate the event
Start the client without the server: `[CLIENT] RMI server unavailable ...` (a `RemoteException` is handled, nothing crashes).
Stop the server while an interactive client runs and send a message: `Remote call failed (server down?)`.
The demo's last two calls (unknown channel, duplicate user) show server-side exceptions returning to the client.

## 12. Expected result
Message stored on the server and returned as an object, `getMessages` returns it, `getOnlineUsers` returns `[Orion]`,
two errors reported as `Server rejected the call`. `./scripts/demo-exp1.sh` ends with `[RESULT] PASS`.

## 13. Viva explanation
"The client does not know any network code. It looks up `MiniDiscordChat` in the RMI registry, receives a stub and calls
`sendMessage` like a local method. The stub serializes the arguments and sends them to the server, where `ChatServiceImpl`
runs the method on the real `ChatState` and the result is serialized back."

## 14. Important viva questions
- *RPC vs RMI?* RPC is the concept; RMI is Java's object-based version (passes objects, uses stubs and the registry).
- *Why must every remote method declare RemoteException?* Network calls can fail in ways local calls cannot.
- *What is the registry for?* Name lookup: it maps a name to a remote object reference.
- *What is a stub?* Client-side proxy that hides marshalling and networking.
- *Why Serializable?* Objects are turned into bytes to cross the network.
- *Does the method run on the client?* No, on the server JVM (shown by the pids).
