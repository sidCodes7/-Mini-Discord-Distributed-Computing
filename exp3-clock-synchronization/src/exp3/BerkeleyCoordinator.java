package exp3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import minidiscord.common.ChatConnection;
import minidiscord.common.Log;

/**
 * Berkeley algorithm - there is no accurate reference clock, the group agrees on its average.
 *   1. the coordinator polls every node for its clock (and compensates RTT/2)
 *   2. it computes each node's difference to its own clock
 *   3. it averages the differences (itself counts as 0; nodes too far away are ignored)
 *   4. it sends every node the amount to ADD to its clock (average - node difference)
 * The nodes are not told the average time, only their own correction.
 */
public class BerkeleyCoordinator {

    /** Where a participating node can be reached. */
    public static class Endpoint {
        final String name;
        final int port;

        public Endpoint(String name, int port) {
            this.name = name;
            this.port = port;
        }
    }

    private static final long OUTLIER_LIMIT_MILLIS = 60_000;

    private final SimulatedClock clock;
    private final List<Endpoint> nodes;

    public BerkeleyCoordinator(SimulatedClock clock, List<Endpoint> nodes) {
        this.clock = clock;
        this.nodes = nodes;
    }

    /** @return the largest remaining difference between any node and the coordinator after the run */
    public long synchronize() throws IOException {
        Log.info("COORDINATOR", "BERKELEY ALGORITHM");
        List<ChatConnection> links = new ArrayList<>();
        try {
            for (Endpoint node : nodes) {
                links.add(new ChatConnection("localhost", node.port, 2000));
            }
            int n = nodes.size();
            long[] reported = new long[n];
            long[] difference = new long[n];
            boolean[] used = new boolean[n];

            Log.info("COORDINATOR", "Step 1: polling clocks");
            for (int i = 0; i < n; i++) {
                long t0 = clock.now();
                reported[i] = Long.parseLong(links.get(i).request("TIME").get(0).trim());
                long t1 = clock.now();
                long estimated = reported[i] + (t1 - t0) / 2;     // node's clock at time t1
                difference[i] = estimated - t1;                   // positive: node is ahead
                used[i] = Math.abs(difference[i]) <= OUTLIER_LIMIT_MILLIS;
                Log.info("COORDINATOR", pad(nodes.get(i).name) + " -> " + Log.time(reported[i])
                        + "   RTT " + (t1 - t0) + " ms, difference to coordinator "
                        + ClockServer.signed(difference[i]) + " ms" + (used[i] ? "" : "  (ignored: too far)"));
            }
            Log.info("COORDINATOR", pad("Coordinator") + " -> " + Log.time(clock.now()) + "   (its own clock, difference 0)");

            long sum = 0;
            int count = 1;
            for (int i = 0; i < n; i++) {
                if (used[i]) {
                    sum += difference[i];
                    count++;
                }
            }
            long average = Math.round((double) sum / count);
            Log.info("COORDINATOR", "Step 2: average difference over " + count + " clocks = "
                    + ClockServer.signed(average) + " ms");

            Log.info("COORDINATOR", "Step 3: sending adjustments");
            long[] adjustment = new long[n];
            for (int i = 0; i < n; i++) {
                adjustment[i] = average - difference[i];
                links.get(i).request("ADJUST " + adjustment[i]);
            }
            long before = clock.now();
            clock.adjust(average);

            Log.info("COORDINATOR", "Step 4: result");
            Log.info("COORDINATOR", pad("Coordinator") + " original " + Log.time(before) + "  difference +0 ms  adjustment "
                    + ClockServer.signed(average) + " ms  updated " + Log.time(clock.now()));
            long maxLeft = 0;
            for (int i = 0; i < n; i++) {
                long t0 = clock.now();
                long afterTime = Long.parseLong(links.get(i).request("TIME").get(0).trim());
                long t1 = clock.now();
                long left = (afterTime + (t1 - t0) / 2) - t1;
                maxLeft = Math.max(maxLeft, Math.abs(left));
                Log.info("COORDINATOR", pad(nodes.get(i).name) + " original " + Log.time(reported[i])
                        + "  difference " + ClockServer.signed(difference[i]) + " ms  adjustment "
                        + ClockServer.signed(adjustment[i]) + " ms  updated " + Log.time(afterTime));
            }
            Log.info("COORDINATOR", "Largest remaining difference to the coordinator: " + maxLeft + " ms");
            return maxLeft;
        } finally {
            for (ChatConnection link : links) {
                link.close();
            }
        }
    }

    private static String pad(String name) {
        return String.format("%-11s", name);
    }
}
