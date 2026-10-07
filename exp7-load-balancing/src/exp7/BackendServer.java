package exp7;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import minidiscord.common.ChatProtocol;
import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.TcpChatServer;

/**
 * One Mini Discord chat server behind the load balancer. workMillis simulates how long a
 * request takes on this machine (a slow server has a large value).
 */
public class BackendServer {
    private final String name;
    private final TcpChatServer server;
    private final AtomicInteger handled = new AtomicInteger();

    public BackendServer(String name, int port, ChatState state, long workMillis) {
        this.name = name;
        this.server = new TcpChatServer(name, port, Executors.newCachedThreadPool(), (client, line) -> {
            Log.sleep(workMillis);
            handled.incrementAndGet();
            List<String> response = ChatProtocol.handle(line, state);
            return response;
        });
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.stop();
        Log.info(name.toUpperCase(), "stopped (simulated server failure)");
    }

    public int getHandled() {
        return handled.get();
    }
}
