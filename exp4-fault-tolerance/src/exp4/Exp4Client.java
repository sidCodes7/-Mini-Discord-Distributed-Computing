package exp4;

import java.io.IOException;
import java.util.List;

import minidiscord.common.ConsoleClient;
import minidiscord.common.Log;

/**
 * EXP 4 client (knows the primary on 5001 and the backup on 5002).
 *   java exp4.Exp4Client before        register, join, send 2 messages to the primary
 *   java exp4.Exp4Client after         send a 3rd message (works only if failover happened), read all
 *   java exp4.Exp4Client interactive   type commands yourself
 */
public class Exp4Client {
    public static final int PRIMARY_PORT = 5001;
    public static final int BACKUP_PORT = 5002;

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "before";
        String tag = "CLIENT";
        Log.banner("4", "FAULT TOLERANCE (CLIENT, " + mode + ")");
        boolean pass = true;
        try (FailoverClient client = new FailoverClient(tag, new String[] {"PRIMARY", "BACKUP"},
                new int[] {PRIMARY_PORT, BACKUP_PORT})) {
            ConsoleClient.Requester r = client::request;
            switch (mode.toLowerCase()) {
                case "before" -> {
                    ConsoleClient.call(tag, r, "REGISTER Orion");
                    ConsoleClient.call(tag, r, "REGISTER Sid");
                    ConsoleClient.call(tag, r, "JOIN Orion #general");
                    ConsoleClient.call(tag, r, "JOIN Sid #general");
                    ConsoleClient.call(tag, r, "SEND Orion #general Message 1 (sent before the failure)");
                    ConsoleClient.call(tag, r, "SEND Sid #general Message 2 (sent before the failure)");
                    pass = expectMessages(ConsoleClient.call(tag, r, "GET #general"), 2);
                }
                case "after" -> {
                    ConsoleClient.call(tag, r, "SEND Orion #general Message 3 (sent after the failover)");
                    pass = expectMessages(ConsoleClient.call(tag, r, "GET #general"), 3);
                    ConsoleClient.call(tag, r, "USERS");
                }
                case "interactive" -> {
                    ConsoleClient.interactive(tag, r);
                    return;
                }
                default -> {
                    Log.info(tag, "Unknown mode. Use before, after or interactive.");
                    return;
                }
            }
        } catch (IOException e) {
            Log.info(tag, "FAILED: " + e.getMessage());
            System.exit(1);
        }
        Log.info("RESULT", pass ? "PASS - chat history is complete on the serving node"
                : "FAIL - chat history is incomplete");
        System.exit(pass ? 0 : 1);
    }

    private static boolean expectMessages(List<String> response, int expected) {
        int actual = Integer.parseInt(response.get(0).substring(3).trim());
        Log.info("CHECK", "Expected " + expected + " messages, server returned " + actual);
        return actual == expected;
    }
}
