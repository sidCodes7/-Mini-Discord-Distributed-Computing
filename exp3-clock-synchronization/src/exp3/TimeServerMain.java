package exp3;

import minidiscord.common.Log;

/**
 * Stand-alone time server for Cristian's algorithm (multi-terminal use).
 *   java exp3.TimeServerMain [offsetMs] [oneWayLatencyMs] [port]
 */
public class TimeServerMain {
    public static void main(String[] args) throws Exception {
        long offset = args.length > 0 ? Long.parseLong(args[0]) : 0;
        long latency = args.length > 1 ? Long.parseLong(args[1]) : 100;
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 7000;
        Log.banner("3", "TIME SERVER (CRISTIAN)");
        ClockServer server = new ClockServer(new SimulatedClock("TimeServer", offset), port, latency);
        server.start();
        Log.info("SERVER", "Time server on port " + port + ", clock offset " + ClockServer.signed(offset)
                + " ms, one-way delay " + latency + " ms");
        Thread.currentThread().join();
    }
}
