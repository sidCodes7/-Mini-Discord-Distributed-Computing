package exp7;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import minidiscord.common.ChatConnection;
import minidiscord.common.ChatProtocol;
import minidiscord.common.Log;
import minidiscord.common.TcpChatServer;

/**
 * EXP 7 - the load balancer. Clients connect to it as if it were the chat server; for every
 * request it picks a backend and forwards the request and the answer.
 *
 *   ROUND_ROBIN       next server in a fixed repeating order (wraps around)
 *   LEAST_CONNECTIONS the server that is currently handling the fewest requests
 *
 * The "connections" counter of a backend is really maintained: it goes up when a request is
 * handed to the server and down when the answer came back.
 */
public class LoadBalancer {
    public enum Strategy { ROUND_ROBIN, LEAST_CONNECTIONS }

    /** A backend chat server as seen by the balancer. */
    public static class Backend {
        final String name;
        final int port;
        final AtomicInteger activeConnections = new AtomicInteger();
        final AtomicInteger totalRequests = new AtomicInteger();
        volatile boolean available = true;

        public Backend(String name, int port) {
            this.name = name;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public int getTotalRequests() {
            return totalRequests.get();
        }
    }

    private final List<Backend> backends = new ArrayList<>();
    private final TcpChatServer server;
    private final AtomicInteger roundRobinIndex = new AtomicInteger();
    private final AtomicInteger detailedLogsLeft = new AtomicInteger();
    private volatile Strategy strategy;

    public LoadBalancer(int port, Strategy strategy) {
        this.strategy = strategy;
        this.server = new TcpChatServer("load-balancer", port, Executors.newCachedThreadPool(), this::route);
    }

    public void addBackend(String name, int port) {
        backends.add(new Backend(name, port));
    }

    public List<Backend> getBackends() {
        return backends;
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        Log.info("LOAD BALANCER", "Algorithm switched to " + strategy);
    }

    /** Print the full decision log for the next n requests (later ones are not logged). */
    public void logNextRequests(int n) {
        detailedLogsLeft.set(n);
    }

    /** Set the connection counters by hand (used to demonstrate the selection rule). */
    public void setConnections(int... counts) {
        for (int i = 0; i < counts.length; i++) {
            backends.get(i).activeConnections.set(counts[i]);
        }
    }

    // ------------------------------------------------------------------ routing

    private List<String> route(String client, String line) {
        boolean log = detailedLogsLeft.getAndDecrement() > 0;
        if (log) {
            Log.info("LOAD BALANCER", "Request received: " + line);
        }
        while (true) {
            Backend backend = selectAndAcquire(log);
            if (backend == null) {
                return ChatProtocol.err("No chat server is available");
            }
            if (log) {
                Log.info("LOAD BALANCER", "Routing request to " + backend.name);
            }
            try (ChatConnection connection = new ChatConnection("localhost", backend.port, 500)) {
                List<String> response = connection.request(line);
                backend.totalRequests.incrementAndGet();
                return response;
            } catch (IOException e) {
                backend.available = false;
                Log.info("LOAD BALANCER", backend.name + " is unreachable (" + e.getMessage()
                        + "), marked unavailable, trying another server");
            } finally {
                backend.activeConnections.decrementAndGet();
            }
        }
    }

    /** Choose a server and count the new connection in one atomic step. */
    private synchronized Backend selectAndAcquire(boolean log) {
        Backend chosen = choose(log);
        if (chosen != null) {
            chosen.activeConnections.incrementAndGet();
        }
        return chosen;
    }

    /** The selection rule itself (also called directly by the demo). */
    public synchronized Backend choose(boolean log) {
        if (log) {
            Log.info("LOAD BALANCER", "Algorithm = " + strategy);
        }
        Backend chosen = null;
        if (strategy == Strategy.ROUND_ROBIN) {
            for (int tries = 0; tries < backends.size() && chosen == null; tries++) {
                Backend candidate = backends.get(Math.floorMod(roundRobinIndex.getAndIncrement(), backends.size()));
                if (candidate.available) {
                    chosen = candidate;
                }
            }
        } else {
            for (Backend b : backends) {
                if (log) {
                    Log.info("LOAD BALANCER", b.name + " connections = " + b.activeConnections.get()
                            + (b.available ? "" : " (unavailable)"));
                }
                if (b.available && (chosen == null || b.activeConnections.get() < chosen.activeConnections.get())) {
                    chosen = b;
                }
            }
        }
        if (log && strategy == Strategy.LEAST_CONNECTIONS && chosen != null) {
            Log.info("LOAD BALANCER", "Selected " + chosen.name);
        }
        return chosen;
    }
}
