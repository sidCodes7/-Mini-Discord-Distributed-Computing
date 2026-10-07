package exp7;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import minidiscord.common.ChatConnection;
import minidiscord.common.ChatState;
import minidiscord.common.Log;

/**
 * EXP 7 demo: a load balancer on port 5200 in front of three chat servers (5201-5203).
 * The servers share one ChatState, like stateless application servers sharing one database,
 * so any server can answer any request. They run at different speeds
 * (Server 1 slow, Server 2 fast, Server 3 medium).
 *   A. Round Robin       9 requests -> 3 / 3 / 3
 *   B. Least Connections the selection rule with counters 20 / 8 / 13
 *   C. Least Connections 60 concurrent requests: the fast server gets the most
 *   D. Server failure    Server 3 dies, the balancer routes around it
 *   java exp7.Exp7Demo
 */
public class Exp7Demo {
    public static void main(String[] args) throws Exception {
        Log.banner("7", "LOAD BALANCING");
        ChatState state = new ChatState();
        BackendServer[] servers = {
                new BackendServer("Server 1", 5201, state, 200),
                new BackendServer("Server 2", 5202, state, 20),
                new BackendServer("Server 3", 5203, state, 80)};
        LoadBalancer lb = new LoadBalancer(5200, LoadBalancer.Strategy.ROUND_ROBIN);
        for (int i = 0; i < servers.length; i++) {
            servers[i].start();
            lb.addBackend("Server " + (i + 1), 5201 + i);
        }
        lb.start();
        Log.info("LOAD BALANCER", "Listening on port 5200, backends on ports 5201, 5202, 5203");
        boolean pass = true;

        // ------------------------------------------------------------------ A. Round Robin
        Log.section("A. ROUND ROBIN - nine requests through the balancer");
        lb.logNextRequests(9);
        try (ChatConnection client = new ChatConnection("localhost", 5200, 2000)) {
            String[] requests = {"REGISTER Orion", "REGISTER Sid", "REGISTER Het", "JOIN Orion #general",
                    "JOIN Sid #general", "JOIN Het #general", "SEND Orion #general Hello via the balancer",
                    "SEND Sid #general Hi Orion", "GET #general"};
            for (String request : requests) {
                client.request(request);
            }
        }
        printDistribution(lb);
        pass &= counts(lb)[0] == 3 && counts(lb)[1] == 3 && counts(lb)[2] == 3;

        // ------------------------------------------------------------------ B. selection rule
        Log.section("B. LEAST CONNECTIONS - selection rule with counters 20 / 8 / 13");
        lb.setStrategy(LoadBalancer.Strategy.LEAST_CONNECTIONS);
        lb.setConnections(20, 8, 13);
        LoadBalancer.Backend picked = lb.choose(true);
        pass &= picked != null && picked.getName().equals("Server 2");
        lb.setConnections(0, 0, 0);

        // ------------------------------------------------------------------ C. concurrent load
        Log.section("C. LEAST CONNECTIONS - 12 clients send 5 messages each at the same time");
        int[] before = counts(lb);
        lb.logNextRequests(3);
        int clientsCount = 12;
        int perClient = 5;
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService clients = Executors.newFixedThreadPool(clientsCount);
        List<Future<?>> futures = new ArrayList<>();
        for (int c = 1; c <= clientsCount; c++) {
            final int number = c;
            futures.add(clients.submit(() -> {
                try (ChatConnection connection = new ChatConnection("localhost", 5200, 2000)) {
                    gate.await();
                    for (int i = 1; i <= perClient; i++) {
                        connection.request("SEND Het #general message " + i + " from client " + number);
                    }
                }
                return null;
            }));
        }
        gate.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        clients.shutdown();
        int[] after = counts(lb);
        int total = 0;
        for (int i = 0; i < 3; i++) {
            Log.info("RESULT", "Server " + (i + 1) + " handled " + (after[i] - before[i]) + " of the "
                    + clientsCount * perClient + " requests");
            total += after[i] - before[i];
        }
        pass &= total == clientsCount * perClient;
        pass &= (after[1] - before[1]) > (after[0] - before[0]);   // fast server beats slow server
        pass &= state.messageCount() == 2 + clientsCount * perClient;
        Log.info("RESULT", "Messages stored: " + state.messageCount() + " (no request lost)");

        // ------------------------------------------------------------------ D. failure
        Log.section("D. SERVER FAILURE - Server 3 stops, Round Robin continues with the others");
        lb.setStrategy(LoadBalancer.Strategy.ROUND_ROBIN);
        servers[2].stop();
        int[] beforeFailure = counts(lb);
        boolean allOk = true;
        try (ChatConnection client = new ChatConnection("localhost", 5200, 2000)) {
            for (int i = 0; i < 6; i++) {
                allOk &= client.request("GET #general").get(0).startsWith("OK");
            }
        }
        int[] afterFailure = counts(lb);
        for (int i = 0; i < 3; i++) {
            Log.info("RESULT", "Server " + (i + 1) + " handled " + (afterFailure[i] - beforeFailure[i]) + " of 6 requests");
        }
        pass &= allOk && afterFailure[2] == beforeFailure[2];
        Log.info("RESULT", allOk ? "All 6 client requests were answered even though Server 3 is down"
                : "Some requests failed");

        Log.section("Result");
        Log.info("RESULT", pass ? "PASS - requests were distributed by Round Robin and Least Connections"
                : "FAIL - unexpected distribution");
        System.exit(pass ? 0 : 1);
    }

    private static int[] counts(LoadBalancer lb) {
        int[] result = new int[lb.getBackends().size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = lb.getBackends().get(i).getTotalRequests();
        }
        return result;
    }

    private static void printDistribution(LoadBalancer lb) {
        int[] c = counts(lb);
        for (int i = 0; i < c.length; i++) {
            Log.info("RESULT", "Server " + (i + 1) + " handled " + c[i] + " requests");
        }
    }
}
