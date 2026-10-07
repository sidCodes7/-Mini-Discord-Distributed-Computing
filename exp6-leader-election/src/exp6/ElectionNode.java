package exp6;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import minidiscord.common.Log;

/**
 * A Mini Discord server node that takes part in coordinator (leader) elections.
 * Every node listens on port 8000 + id and talks to the others over short TCP messages:
 *
 *   PING                              -> PONG
 *   ELECTION <fromId>                 -> OK            (Bully)
 *   COORDINATOR <id>                  -> ACK           (Bully announcement)
 *   RING_ELECTION <initiator> <ids>   -> ACK           (Ring, ids = active nodes collected so far)
 *   RING_COORDINATOR <id> <initiator> -> ACK           (Ring announcement)
 *
 * A crashed node has closed its port, so a message to it fails: "no response".
 */
public class ElectionNode {
    public static final int[] ALL_IDS = {1, 2, 3, 4, 5};
    public static final int BASE_PORT = 8000;
    private static final int CONNECT_TIMEOUT_MILLIS = 300;
    private static final int READ_TIMEOUT_MILLIS = 1500;
    private static final long WAIT_FOR_ANNOUNCEMENT_MILLIS = 3000;

    private final int id;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean electionRunning = new AtomicBoolean();
    private volatile int coordinatorId = -1;
    private volatile long announcements;
    private volatile boolean alive;
    private ServerSocket serverSocket;

    public ElectionNode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public int getCoordinatorId() {
        return coordinatorId;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setCoordinator(int coordinator) {
        this.coordinatorId = coordinator;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(BASE_PORT + id));
        alive = true;
        Thread acceptor = new Thread(this::acceptLoop, "node-" + id + "-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** Simulates a crash: the port closes, so nobody can reach this node any more. */
    public void crash() {
        alive = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        Log.info("NODE " + id, "CRASHED (port " + (BASE_PORT + id) + " closed)");
    }

    public void stop() {
        alive = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (alive) {
            try {
                Socket socket = serverSocket.accept();
                pool.submit(() -> serve(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            String line = in.readLine();
            if (line != null) {
                out.println(handle(line.trim()));
            }
        } catch (IOException ignored) {
            // peer went away
        }
    }

    private String handle(String line) {
        String[] p = line.split("\\s+");
        switch (p[0]) {
            case "PING":
                return "PONG";
            case "ELECTION": {
                int from = Integer.parseInt(p[1]);
                Log.info("NODE " + id, "Election message received from Node " + from);
                Log.info("NODE " + id, "Responding OK");
                if (coordinatorId == id) {
                    pool.submit(this::announceCoordinator);
                } else {
                    pool.submit(this::startBullyElection);
                }
                return "OK";
            }
            case "COORDINATOR": {
                coordinatorId = Integer.parseInt(p[1]);
                announcements++;
                Log.info("NODE " + id, "Accepted Node " + coordinatorId + " as the new coordinator");
                return "ACK";
            }
            case "RING_ELECTION": {
                int initiator = Integer.parseInt(p[1]);
                List<Integer> ids = new ArrayList<>();
                for (String s : p[2].split(",")) {
                    ids.add(Integer.parseInt(s));
                }
                pool.submit(() -> handleRingElection(initiator, ids));
                return "ACK";
            }
            case "RING_COORDINATOR": {
                int winner = Integer.parseInt(p[1]);
                int initiator = Integer.parseInt(p[2]);
                pool.submit(() -> handleRingCoordinator(winner, initiator));
                return "ACK";
            }
            default:
                return "ERR";
        }
    }

    /** Send one message to a node and return its reply, or null if it did not respond. */
    private String send(int to, String message) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", BASE_PORT + to), CONNECT_TIMEOUT_MILLIS);
            s.setSoTimeout(READ_TIMEOUT_MILLIS);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out.println(message);
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Bully algorithm

    /** Ping the coordinator; if it does not answer, start an election. */
    public void detectCoordinatorFailure() {
        int coordinator = coordinatorId;
        Log.info("NODE " + id, "Checking coordinator Node " + coordinator);
        if (send(coordinator, "PING") == null) {
            Log.info("NODE " + id, "Coordinator Node " + coordinator + " is not responding");
            startBullyElection();
        } else {
            Log.info("NODE " + id, "Coordinator Node " + coordinator + " is alive");
        }
    }

    /**
     * Bully election: contact every node with a higher id. If none answers, this node is the
     * highest active node and becomes coordinator. If somebody answers OK, that node takes over
     * the election (it runs the same procedure) and this node waits for the announcement.
     */
    public void startBullyElection() {
        if (!alive || !electionRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int attempt = 1; attempt <= 3 && alive; attempt++) {
                long seenAnnouncements = announcements;
                Log.info("NODE " + id, "Starting election");
                boolean someoneHigherAnswered = false;
                for (int higher : ALL_IDS) {
                    if (higher > id) {
                        Log.info("NODE " + id, "Sending ELECTION to Node " + higher);
                        if ("OK".equals(send(higher, "ELECTION " + id))) {
                            someoneHigherAnswered = true;
                        } else {
                            Log.info("NODE " + id, "No response from Node " + higher);
                        }
                    }
                }
                if (!someoneHigherAnswered) {
                    becomeCoordinator();
                    return;
                }
                long deadline = System.currentTimeMillis() + WAIT_FOR_ANNOUNCEMENT_MILLIS;
                while (alive && announcements == seenAnnouncements && System.currentTimeMillis() < deadline) {
                    Log.sleep(50);
                }
                if (announcements != seenAnnouncements) {
                    return;
                }
                Log.info("NODE " + id, "No coordinator announcement arrived, restarting election");
            }
        } finally {
            electionRunning.set(false);
        }
    }

    private void becomeCoordinator() {
        coordinatorId = id;
        announcements++;
        Log.info("NODE " + id, "Becoming coordinator");
        announceCoordinator();
    }

    private void announceCoordinator() {
        List<Integer> reached = new ArrayList<>();
        for (int other : ALL_IDS) {
            if (other != id) {
                if (send(other, "COORDINATOR " + id) != null) {
                    reached.add(other);
                }
            }
        }
        Log.info("NODE " + id, "Coordinator announcement sent to Nodes " + reached);
    }

    // ------------------------------------------------------------------ Ring algorithm

    /** Start a ring election: a message that collects the ids of all active nodes travels round the ring. */
    public void startRingElection() {
        Log.info("RING", "Election initiated by Node " + id);
        List<Integer> ids = new ArrayList<>();
        ids.add(id);
        forwardInRing("RING_ELECTION " + id + " " + join(ids), "election message");
    }

    private void handleRingElection(int initiator, List<Integer> ids) {
        if (id == initiator) {
            int winner = ids.stream().mapToInt(Integer::intValue).max().orElse(id);
            Log.info("RING", "Election message is back at Node " + id + " having visited nodes " + ids);
            Log.info("RING", "Coordinator elected: Node " + winner);
            coordinatorId = winner;
            announcements++;
            forwardInRing("RING_COORDINATOR " + winner + " " + id, "coordinator message");
        } else {
            List<Integer> extended = new ArrayList<>(ids);
            extended.add(id);
            forwardInRing("RING_ELECTION " + initiator + " " + join(extended), "election message");
        }
    }

    private void handleRingCoordinator(int winner, int initiator) {
        coordinatorId = winner;
        announcements++;
        Log.info("RING", "Node " + id + " now knows the coordinator is Node " + winner);
        if (id == initiator) {
            Log.info("RING", "Coordinator message completed the circle");
        } else {
            forwardInRing("RING_COORDINATOR " + winner + " " + initiator, "coordinator message");
        }
    }

    /** Pass a message to the next node in the ring that answers (failed nodes are skipped). */
    private void forwardInRing(String message, String what) {
        int index = indexOf(id);
        for (int step = 1; step <= ALL_IDS.length; step++) {
            int candidate = ALL_IDS[(index + step) % ALL_IDS.length];
            if (send(candidate, message) != null) {
                Log.info("RING", "Node " + id + " forwarded " + what + " to Node " + candidate);
                return;
            }
            Log.info("RING", "Node " + candidate + " is not responding, skipping it");
        }
    }

    private static int indexOf(int nodeId) {
        for (int i = 0; i < ALL_IDS.length; i++) {
            if (ALL_IDS[i] == nodeId) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid node id " + nodeId);
    }

    private static String join(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
