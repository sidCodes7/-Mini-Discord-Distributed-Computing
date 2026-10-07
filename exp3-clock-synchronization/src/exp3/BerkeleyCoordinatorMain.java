package exp3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import minidiscord.common.Log;

/**
 * Stand-alone Berkeley coordinator (multi-terminal use). Node i must be listening on port 7000 + i.
 *   java exp3.BerkeleyCoordinatorMain [coordinatorOffsetMs] [numberOfNodes]
 */
public class BerkeleyCoordinatorMain {
    public static void main(String[] args) {
        long offset = args.length > 0 ? Long.parseLong(args[0]) : 1500;
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        Log.banner("3", "BERKELEY COORDINATOR");
        SimulatedClock clock = new SimulatedClock("Coordinator", offset);
        List<BerkeleyCoordinator.Endpoint> nodes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            nodes.add(new BerkeleyCoordinator.Endpoint("Node " + i, 7000 + i));
        }
        try {
            new BerkeleyCoordinator(clock, nodes).synchronize();
        } catch (IOException e) {
            Log.info("COORDINATOR", "Cannot reach a node: " + e.getMessage()
                    + " (start ClockNodeMain for ports 7001.." + (7000 + count) + " first)");
        }
    }
}
