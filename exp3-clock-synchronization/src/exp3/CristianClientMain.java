package exp3;

import java.io.IOException;

import minidiscord.common.Log;

/**
 * Stand-alone Cristian client (multi-terminal use).
 *   java exp3.CristianClientMain [name] [offsetMs] [serverPort]
 */
public class CristianClientMain {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "Node A";
        long offset = args.length > 1 ? Long.parseLong(args[1]) : 5000;
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 7000;
        Log.banner("3", "CRISTIAN CLIENT " + name);
        SimulatedClock clock = new SimulatedClock(name, offset);
        Log.info("CLOCK " + name, "Local time before synchronization: " + Log.time(clock.now())
                + " (offset " + ClockServer.signed(offset) + " ms)");
        try {
            CristianClient.synchronize(clock, "localhost", port);
        } catch (IOException e) {
            Log.info("CLOCK " + name, "Cannot reach the time server: " + e.getMessage()
                    + " (start TimeServerMain first)");
        }
    }
}
