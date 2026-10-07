package exp6;

import java.util.ArrayList;
import java.util.List;

import minidiscord.common.Log;

/**
 * EXP 6 demo: five server nodes on ports 8001-8005 (inside one JVM, talking over real sockets).
 * Node 5 is the coordinator and crashes; Node 2 notices and an election chooses Node 4.
 *   java exp6.Exp6Demo            both algorithms
 *   java exp6.Exp6Demo bully      only Bully
 *   java exp6.Exp6Demo ring       only Ring
 */
public class Exp6Demo {
    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0].toLowerCase() : "both";
        Log.banner("6", "LEADER ELECTION (BULLY + RING)");
        boolean pass = true;
        if (which.equals("both") || which.equals("bully")) {
            pass &= run("BULLY ALGORITHM", true);
        }
        if (which.equals("both") || which.equals("ring")) {
            pass &= run("RING ALGORITHM", false);
        }
        Log.section("Result");
        Log.info("RESULT", pass ? "PASS - every surviving node agrees that Node 4 is the coordinator"
                : "FAIL - nodes disagree about the coordinator");
        System.exit(pass ? 0 : 1);
    }

    private static boolean run(String title, boolean bully) throws Exception {
        Log.section(title);
        List<ElectionNode> nodes = new ArrayList<>();
        for (int id : ElectionNode.ALL_IDS) {
            ElectionNode node = new ElectionNode(id);
            node.start();
            node.setCoordinator(5);
            nodes.add(node);
        }
        Log.info("ELECTION", "Nodes 1-5 are running, Node 5 (highest id) is the coordinator");
        ElectionNode failing = nodes.get(4);
        failing.crash();
        Log.info("ELECTION", "Node 5 was the coordinator and is now down");
        System.out.println();

        ElectionNode detector = nodes.get(1);
        if (bully) {
            detector.detectCoordinatorFailure();
        } else {
            Log.info("NODE 2", "Coordinator Node 5 is not responding, starting a ring election");
            detector.startRingElection();
        }

        boolean agreed = waitForAgreement(nodes, 4, 10_000);
        System.out.println();
        for (ElectionNode node : nodes) {
            if (node.isAlive()) {
                Log.info("ELECTION", "Node " + node.getId() + " believes the coordinator is Node " + node.getCoordinatorId());
            } else {
                Log.info("ELECTION", "Node " + node.getId() + " is down");
            }
        }
        for (ElectionNode node : nodes) {
            node.stop();
        }
        Log.sleep(300);
        return agreed;
    }

    private static boolean waitForAgreement(List<ElectionNode> nodes, int expected, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean all = true;
            for (ElectionNode node : nodes) {
                if (node.isAlive() && node.getCoordinatorId() != expected) {
                    all = false;
                }
            }
            if (all) {
                Log.sleep(300);   // let the last log lines of the announcement appear
                return true;
            }
            Log.sleep(50);
        }
        return false;
    }
}
