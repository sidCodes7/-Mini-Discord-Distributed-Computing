package exp4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import minidiscord.common.ChatProtocol;
import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.Message;
import minidiscord.common.TcpChatServer;

/**
 * EXP 4 - one chat server process that is either ACTIVE (serves clients, replicates to its
 * peer, sends heartbeats) or STANDBY (receives replicated state, watches the heartbeat).
 *
 *   ACTIVE ----- SNAPSHOT + update lines + HEARTBEAT (TCP, replication port) ----> STANDBY
 *
 * If the standby sees no heartbeat for timeoutMs it declares the active node failed, opens
 * the client port and becomes ACTIVE (failover). A crashed node that is started again as
 * STANDBY receives a full snapshot from the new ACTIVE node (recovery).
 *
 *   java exp4.FaultTolerantNode <name> <primary|backup> <clientPort> <replPort> <peerReplPort> [timeoutMs]
 *
 * Console commands: STATUS, MESSAGES, FAIL (simulate a crash), QUIT
 */
public class FaultTolerantNode {
    private enum Role { ACTIVE, STANDBY }

    private static final long HEARTBEAT_INTERVAL_MILLIS = 1000;

    private final String name;
    private final int clientPort;
    private final int replPort;
    private final int peerReplPort;
    private final long timeoutMillis;
    private final ChatState state = new ChatState();
    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>();

    private volatile Role role;
    private volatile boolean heartbeatSeen;
    private volatile long lastHeartbeat;
    private TcpChatServer clientServer;

    private FaultTolerantNode(String name, Role role, int clientPort, int replPort, int peerReplPort, long timeoutMillis) {
        this.name = name;
        this.role = role;
        this.clientPort = clientPort;
        this.replPort = replPort;
        this.peerReplPort = peerReplPort;
        this.timeoutMillis = timeoutMillis;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.out.println("Usage: java exp4.FaultTolerantNode <name> <primary|backup> <clientPort> <replPort> <peerReplPort> [timeoutMs]");
            return;
        }
        Role role = args[1].equalsIgnoreCase("primary") ? Role.ACTIVE : Role.STANDBY;
        long timeout = args.length > 5 ? Long.parseLong(args[5]) : 3000;
        Log.banner("4", "FAULT TOLERANCE (" + args[0].toUpperCase() + ")");
        FaultTolerantNode node = new FaultTolerantNode(args[0].toUpperCase(), role, Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), Integer.parseInt(args[4]), timeout);
        node.start();
        Thread.currentThread().join();
    }

    private void start() throws IOException {
        startReplicationReceiver();
        Log.info(name, "Role " + role + ", client port " + clientPort + ", replication port " + replPort
                + ", failure timeout " + timeoutMillis + " ms");
        if (role == Role.ACTIVE) {
            becomeActive();
        } else {
            Log.info(name, "Standby: waiting for heartbeats from the active server");
            daemon("monitor", this::monitorLoop);
        }
        daemon("console", this::consoleLoop);
    }

    // ------------------------------------------------------------------ ACTIVE side

    private void becomeActive() throws IOException {
        role = Role.ACTIVE;
        state.setUpdateListener(outbox::offer);
        clientServer = new TcpChatServer(name + "-clients", clientPort, Executors.newCachedThreadPool(), (client, line) -> {
            List<String> response = ChatProtocol.handle(line, state);
            Log.info(name, "Request '" + line + "' -> " + response.get(0));
            return response;
        });
        clientServer.start();
        Log.info(name, "ACTIVE - serving clients on port " + clientPort);
        daemon("replication-sender", this::senderLoop);
    }

    /** Keeps a connection to the peer: snapshot first, then every update, or a heartbeat when idle. */
    private void senderLoop() {
        boolean reportedDown = false;
        while (role == Role.ACTIVE) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", peerReplPort), 500);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                outbox.clear();
                List<String> snapshot = state.snapshotOps();
                out.println("SNAPSHOT_BEGIN");
                for (String op : snapshot) {
                    out.println(op);
                }
                out.println("SNAPSHOT_END");
                Log.info("REPLICATION", name + " connected to standby, snapshot of " + snapshot.size() + " operations sent");
                reportedDown = false;
                while (role == Role.ACTIVE) {
                    String op = outbox.poll(HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                    if (op != null) {
                        out.println(op);
                        Log.info("REPLICATION", "Sending update to standby: " + describe(op));
                    } else {
                        out.println("HEARTBEAT");
                        Log.info(name, "Heartbeat sent");
                    }
                    if (out.checkError()) {
                        throw new IOException("standby closed the connection");
                    }
                }
            } catch (IOException e) {
                if (!reportedDown) {
                    Log.info(name, "Standby unavailable (" + e.getMessage() + "); updates are kept in the state and"
                            + " a full snapshot is sent when it returns");
                    reportedDown = true;
                }
                Log.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ STANDBY side

    private void startReplicationReceiver() throws IOException {
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(replPort));
        daemon("replication-receiver", () -> {
            while (true) {
                try {
                    Socket socket = server.accept();
                    daemon("replication-link", () -> receive(socket));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    private void receive(Socket socket) {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (role != Role.STANDBY) {
                    continue; // an ACTIVE node never overwrites its own state
                }
                lastHeartbeat = System.currentTimeMillis();
                heartbeatSeen = true;
                switch (line) {
                    case "HEARTBEAT" -> Log.info(name, "Primary heartbeat received");
                    case "SNAPSHOT_BEGIN" -> {
                        state.reset();
                        Log.info(name, "Receiving full state snapshot from the active server");
                    }
                    case "SNAPSHOT_END" -> Log.info(name, "Snapshot loaded: " + state.getRegisteredUsers().size()
                            + " users, " + state.messageCount() + " messages, last message id " + state.lastMessageId());
                    default -> {
                        state.applyOp(line);
                        Log.info("REPLICATION", name + " update received: " + describe(line));
                    }
                }
            }
        } catch (IOException ignored) {
            // link dropped; the monitor decides whether this means failure
        }
    }

    private void monitorLoop() {
        while (role == Role.STANDBY) {
            Log.sleep(250);
            if (heartbeatSeen && System.currentTimeMillis() - lastHeartbeat > timeoutMillis) {
                failover();
                return;
            }
        }
    }

    private void failover() {
        Log.info(name, "Primary failure detected (no heartbeat for more than " + timeoutMillis + " ms)");
        Log.info("FAULT TOLERANCE", "Primary failed");
        Log.info("FAULT TOLERANCE", "Backup taking over (state: " + state.getRegisteredUsers().size() + " users, "
                + state.messageCount() + " messages, last id " + state.lastMessageId() + ")");
        try {
            becomeActive();
            Log.info("FAULT TOLERANCE", "Failover complete");
        } catch (IOException e) {
            Log.info("FAULT TOLERANCE", "Failover FAILED: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ console

    private void consoleLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = in.readLine()) != null) {
                switch (line.trim().toUpperCase()) {
                    case "FAIL" -> {
                        Log.info(name, "FAIL command: simulating a crash now");
                        Runtime.getRuntime().halt(1);   // no cleanup, like a real crash
                    }
                    case "STATUS" -> Log.info(name, "role=" + role + ", users=" + state.getRegisteredUsers()
                            + ", messages=" + state.messageCount() + ", lastId=" + state.lastMessageId());
                    case "MESSAGES" -> {
                        for (Message m : state.getMessages("#general")) {
                            Log.info(name, "   " + m);
                        }
                    }
                    case "QUIT" -> System.exit(0);
                    default -> Log.info(name, "Commands: STATUS, MESSAGES, FAIL, QUIT");
                }
            }
        } catch (IOException ignored) {
            // no console available
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String describe(String op) {
        return ChatState.describeOp(op);
    }

    private static void daemon(String threadName, Runnable task) {
        Thread t = new Thread(task, threadName);
        t.setDaemon(true);
        t.start();
    }
}
