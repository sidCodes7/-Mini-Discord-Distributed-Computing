package exp2;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import minidiscord.common.ChatProtocol;
import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.TcpChatServer;

/**
 * EXP 2 - a chat server that serves many clients at the same time.
 *
 * A fixed pool of named worker threads (Worker-1 ... Worker-N, an ExecutorService) handles the
 * connections. All workers share ONE ChatState, which is safe because it is built from
 * ConcurrentHashMap, ConcurrentLinkedQueue and AtomicLong (see ChatState).
 *
 *   java exp2.ConcurrentChatServer [port] [workers]
 */
public class ConcurrentChatServer {
    public static final int PORT = 5100;

    private final ChatState state;
    private final int printLimit;
    private final AtomicInteger printed = new AtomicInteger();
    private final Map<String, String> sessionUser = new ConcurrentHashMap<>();
    private final TcpChatServer server;

    public ConcurrentChatServer(int port, int workers, ChatState state, int printLimit) {
        this.state = state;
        this.printLimit = printLimit;
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> new Thread(r, "Worker-" + counter.incrementAndGet());
        ExecutorService pool = Executors.newFixedThreadPool(workers, factory);
        this.server = new TcpChatServer("exp2-server", port, pool, this::handle);
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public ChatState getState() {
        return state;
    }

    private List<String> handle(String client, String line) {
        String[] p = line.trim().split("\\s+", 4);
        String command = p[0].toUpperCase();
        if ((command.equals("REGISTER") || command.equals("LOGIN")) && p.length > 1) {
            sessionUser.put(client, p[1]);
        }
        String who = (p.length > 1 && !command.equals("GET")) ? p[1] : sessionUser.getOrDefault(client, "client");
        if (printed.incrementAndGet() <= printLimit) {
            Log.info(Thread.currentThread().getName(), who + " " + describe(command));
        }
        return ChatProtocol.handle(line, state);
    }

    private static String describe(String command) {
        return switch (command) {
            case "SEND" -> "sending message";
            case "GET" -> "retrieving messages";
            case "REGISTER" -> "registering";
            case "JOIN" -> "joining a channel";
            default -> command.toLowerCase();
        };
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        int workers = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        Log.banner("2", "MULTITHREADING (SERVER)");
        ConcurrentChatServer server = new ConcurrentChatServer(port, workers, new ChatState(), Integer.MAX_VALUE);
        server.start();
        Log.info("SERVER", "Listening on port " + port + " with " + workers + " worker threads");
        Thread.currentThread().join();
    }
}
