package exp0;

import java.util.List;
import java.util.concurrent.Executors;

import minidiscord.common.ChatProtocol;
import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.TcpChatServer;

/**
 * EXP 0 - the base Mini Discord server: an independent process that owns the chat state
 * (users, channels, membership, messages) and serves any number of clients over TCP.
 */
public class Exp0Server {
    public static final int PORT = 5000;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        Log.banner("0", "DISTRIBUTED SYSTEM (BASE CHAT SERVER)");

        ChatState state = new ChatState();
        TcpChatServer server = new TcpChatServer("exp0-server", port, Executors.newCachedThreadPool(),
                (client, line) -> {
                    List<String> response = ChatProtocol.handle(line, state);
                    Log.info("SERVER", client + " sent '" + line + "' -> " + response.get(0));
                    return response;
                });
        server.start();
        Log.info("SERVER", "Mini Discord server listening on port " + port);
        Log.info("SERVER", "Channels: " + ChatState.DEFAULT_CHANNELS);
        Thread.currentThread().join();
    }
}
