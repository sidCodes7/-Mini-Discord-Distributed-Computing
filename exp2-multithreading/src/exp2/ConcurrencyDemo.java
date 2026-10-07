package exp2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import minidiscord.common.ChatConnection;
import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.Message;

/**
 * EXP 2 demo: eight real TCP clients (eight client threads) hammer one server at the same time.
 * Afterwards we check that the shared state is still correct: no lost messages, no duplicate ids.
 *   java exp2.ConcurrencyDemo
 */
public class ConcurrencyDemo {
    private static final String[] USERS = {"Orion", "Sid", "Het", "Ava", "Ben", "Cleo", "Dev", "Eli"};
    private static final int MESSAGES_PER_USER = 25;

    public static void main(String[] args) throws Exception {
        Log.banner("2", "MULTITHREADING / CONCURRENCY");
        int port = ConcurrentChatServer.PORT;
        ChatState state = new ChatState();
        // Only the first 14 requests are printed so the thread names stay readable.
        ConcurrentChatServer server = new ConcurrentChatServer(port, USERS.length, state, 14);
        server.start();
        Log.info("SERVER", "Listening on port " + port + " with " + USERS.length + " worker threads");

        // Every user registers and joins #general before the concurrent part starts.
        for (String user : USERS) {
            state.registerUser(user);
            state.joinChannel(user, "#general");
        }
        Log.section("Starting " + USERS.length + " clients at the same moment");

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService clients = Executors.newFixedThreadPool(USERS.length);
        List<Future<List<Long>>> results = new ArrayList<>();
        for (String user : USERS) {
            Callable<List<Long>> task = () -> runClient(user, port, startGate);
            results.add(clients.submit(task));
        }
        long start = System.nanoTime();
        startGate.countDown();

        Set<Long> ids = new HashSet<>();
        int sent = 0;
        for (Future<List<Long>> f : results) {
            for (long id : f.get()) {
                sent++;
                ids.add(id);
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        clients.shutdown();

        List<Message> stored = state.getMessages("#general");
        long maxId = stored.isEmpty() ? 0 : stored.get(stored.size() - 1).getId();
        Log.section("Verification");
        Log.info("CHECK", "Messages sent by clients : " + sent);
        Log.info("CHECK", "Messages stored on server: " + stored.size());
        Log.info("CHECK", "Distinct message ids     : " + ids.size() + " (highest id " + maxId + ")");
        Log.info("CHECK", "Elapsed (all clients)    : " + elapsedMs + " ms");
        boolean pass = sent == USERS.length * MESSAGES_PER_USER && stored.size() == sent
                && ids.size() == sent && maxId == sent;
        Log.info("RESULT", pass ? "PASS - no message lost, every id unique (AtomicLong + concurrent collections)"
                : "FAIL - shared state was corrupted");
        server.stop();
        System.exit(pass ? 0 : 1);
    }

    /** One client thread: its own connection, its own sequence of requests. */
    private static List<Long> runClient(String user, int port, CountDownLatch startGate) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (ChatConnection connection = new ChatConnection("localhost", port, 2000)) {
            connection.request("LOGIN " + user); // lets the server name this connection in its log
            startGate.await();
            for (int i = 1; i <= MESSAGES_PER_USER; i++) {
                String reply = connection.request("SEND " + user + " #general message " + i + " from " + user).get(0);
                ids.add(Long.parseLong(reply.split("\\s+")[2]));
                if (user.equals("Het") && i % 5 == 0) {
                    connection.request("GET #general"); // readers run alongside writers
                }
            }
        }
        return ids;
    }
}
