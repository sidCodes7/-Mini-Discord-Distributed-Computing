package exp0;

import java.io.IOException;

import minidiscord.common.ChatConnection;
import minidiscord.common.ConsoleClient;
import minidiscord.common.Log;

/**
 * EXP 0 client.
 *   java exp0.Exp0Client Orion demo          scripted scenario for user Orion
 *   java exp0.Exp0Client Orion interactive   type commands yourself
 * Optional third argument: server port (default 5000).
 */
public class Exp0Client {
    public static void main(String[] args) throws IOException {
        String user = args.length > 0 ? args[0] : "Orion";
        String mode = args.length > 1 ? args[1] : "demo";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : Exp0Server.PORT;
        String tag = "CLIENT " + user;

        Log.banner("0", "DISTRIBUTED SYSTEM (CLIENT " + user + ")");
        try (ChatConnection connection = new ChatConnection("localhost", port, 2000)) {
            Log.info(tag, "Connected to server on localhost:" + port);
            if (mode.equalsIgnoreCase("interactive")) {
                ConsoleClient.interactive(tag, connection::request);
                return;
            }
            ConsoleClient.call(tag, connection::request, "REGISTER " + user);
            ConsoleClient.call(tag, connection::request, "JOIN " + user + " #general");
            ConsoleClient.call(tag, connection::request, "JOIN " + user + " #coding");
            ConsoleClient.call(tag, connection::request, "SEND " + user + " #general Hello from " + user);
            ConsoleClient.call(tag, connection::request, "SEND " + user + " #coding Distributed systems are fun");
            ConsoleClient.call(tag, connection::request, "GET #general");
            ConsoleClient.call(tag, connection::request, "USERS");
            Log.section("Error handling");
            ConsoleClient.call(tag, connection::request, "REGISTER " + user);
            ConsoleClient.call(tag, connection::request, "SEND " + user + " #nowhere hi");
            ConsoleClient.call(tag, connection::request, "SEND " + user + " #general    ");
        } catch (IOException e) {
            Log.info(tag, "Cannot reach the server: " + e.getMessage()
                    + " (start Exp0Server first)");
        }
    }
}
