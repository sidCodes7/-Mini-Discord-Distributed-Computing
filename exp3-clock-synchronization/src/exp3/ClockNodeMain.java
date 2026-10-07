package exp3;

import minidiscord.common.Log;

/**
 * Stand-alone Berkeley participant (multi-terminal use).
 *   java exp3.ClockNodeMain <name> <offsetMs> <port>      e.g.  Node1 5000 7001
 */
public class ClockNodeMain {
    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "Node1";
        long offset = args.length > 1 ? Long.parseLong(args[1]) : 5000;
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 7001;
        Log.banner("3", "BERKELEY NODE " + name);
        ClockServer node = new ClockServer(new SimulatedClock(name, offset), port, 40);
        node.start();
        Log.info(name.toUpperCase(), "Listening on port " + port + ", clock offset " + ClockServer.signed(offset)
                + " ms, local time " + Log.time(node.getClock().now()));
        Thread.currentThread().join();
    }
}
