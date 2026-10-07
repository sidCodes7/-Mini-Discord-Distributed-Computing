package exp2;

import java.io.IOException;

import minidiscord.common.ChatConnection;
import minidiscord.common.ConsoleClient;
import minidiscord.common.Log;

/**
 * EXP 2 client for multi-terminal use: start several of them against one ConcurrentChatServer.
 *   java exp2.ConcurrentChatClient Orion demo          sends 10 messages quickly
 *   java exp2.ConcurrentChatClient Orion interactive
 */
public class ConcurrentChatClient {
    public static void main(String[] args) throws IOException {
        String user = args.length > 0 ? args[0] : "Orion";
        String mode = args.length > 1 ? args[1] : "demo";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : ConcurrentChatServer.PORT;
        String tag = "CLIENT " + user;

        try (ChatConnection connection = new ChatConnection("localhost", port, 2000)) {
            Log.info(tag, "Connected to localhost:" + port);
            if (mode.equalsIgnoreCase("interactive")) {
                ConsoleClient.interactive(tag, connection::request);
                return;
            }
            Log.info(tag, connection.request("REGISTER " + user).get(0));
            Log.info(tag, connection.request("JOIN " + user + " #general").get(0));
            for (int i = 1; i <= 10; i++) {
                Log.info(tag, connection.request("SEND " + user + " #general message " + i + " from " + user).get(0));
            }
            ConsoleClient.call(tag, connection::request, "GET #general");
        } catch (IOException e) {
            Log.info(tag, "Cannot reach the server: " + e.getMessage() + " (start ConcurrentChatServer first)");
        }
    }
}
