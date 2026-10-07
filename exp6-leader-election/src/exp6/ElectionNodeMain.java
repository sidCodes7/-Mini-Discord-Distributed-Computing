package exp6;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import minidiscord.common.Log;

/**
 * One election node as its own process (multi-terminal use). Start nodes 1..5, then type
 * commands in any terminal:
 *   bully    ping the coordinator and, if it is down, run a Bully election
 *   ring     start a Ring election
 *   status   show who this node thinks the coordinator is
 *   crash    simulate a crash (closes the port, the process stays so you can restart nothing)
 *   quit
 *   java exp6.ElectionNodeMain <id 1-5>
 */
public class ElectionNodeMain {
    public static void main(String[] args) throws Exception {
        int id = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        if (id < 1 || id > 5) {
            System.out.println("Node id must be between 1 and 5");
            return;
        }
        Log.banner("6", "ELECTION NODE " + id);
        ElectionNode node = new ElectionNode(id);
        node.start();
        node.setCoordinator(5);
        Log.info("NODE " + id, "Listening on port " + (ElectionNode.BASE_PORT + id) + ", coordinator is Node 5");
        Log.info("NODE " + id, "Commands: bully, ring, status, crash, quit");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            switch (line.trim().toLowerCase()) {
                case "bully" -> node.detectCoordinatorFailure();
                case "ring" -> node.startRingElection();
                case "status" -> Log.info("NODE " + id, "coordinator = Node " + node.getCoordinatorId());
                case "crash" -> node.crash();
                case "quit" -> {
                    node.stop();
                    return;
                }
                default -> Log.info("NODE " + id, "Commands: bully, ring, status, crash, quit");
            }
        }
        Thread.currentThread().join();
    }
}
