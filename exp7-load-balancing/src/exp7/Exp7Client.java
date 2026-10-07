package exp7;

import java.io.IOException;

import minidiscord.common.ChatConnection;
import minidiscord.common.ConsoleClient;
import minidiscord.common.Log;

/**
 * A normal chat client that talks to the load balancer on port 5200.
 *   java exp7.Exp7Client Orion demo | interactive
 * (With separate backend processes every server has its own state, so use the commands the
 * server in question knows; see the README.)
 */
public class Exp7Client {
    public static void main(String[] args) throws IOException {
        String user = args.length > 0 ? args[0] : "Orion";
        String mode = args.length > 1 ? args[1] : "demo";
        String tag = "CLIENT " + user;
        try (ChatConnection connection = new ChatConnection("localhost", 5200, 2000)) {
            Log.info(tag, "Connected to the load balancer on port 5200");
            if (mode.equalsIgnoreCase("interactive")) {
                ConsoleClient.interactive(tag, connection::request);
                return;
            }
            for (int i = 1; i <= 6; i++) {
                ConsoleClient.call(tag, connection::request, "REGISTER " + user + i);
            }
        } catch (IOException e) {
            Log.info(tag, "Cannot reach the load balancer: " + e.getMessage());
        }
    }
}
