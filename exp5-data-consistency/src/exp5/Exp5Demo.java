package exp5;

import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.Message;

/**
 * EXP 5 demo - replicated chat state and consistency.
 *   Scenario 1  synchronous replication: both copies are always equal
 *   Scenario 2  delayed replication + primary crash: the backup is STALE and data is lost
 *   Scenario 3  delayed replication + synchronization: the stale backup catches up
 *   java exp5.Exp5Demo
 *
 * The primary and the backup are two separate ReplicaNode objects with separate ChatState
 * copies; every change travels as an operation line over a ReplicationLink (see Exp 4 for the
 * same idea between real processes).
 */
public class Exp5Demo {
    /** A primary, a backup and the link between them. */
    private static class Cluster {
        final ReplicaNode primary = new ReplicaNode("PRIMARY");
        final ReplicaNode backup = new ReplicaNode("BACKUP");
        final ReplicationLink link = new ReplicationLink(backup);

        Cluster() {
            primary.getState().setUpdateListener(op -> {
                if (op.startsWith("MSG|")) {
                    Log.info("PRIMARY", "Message added");
                } else {
                    Log.info("PRIMARY", "State changed: " + ChatState.describeOp(op));
                }
                link.replicate(op);
            });
        }

        void setUp() {
            primary.getState().registerUser("Orion");
            primary.getState().registerUser("Sid");
            primary.getState().joinChannel("Orion", "#general");
            primary.getState().joinChannel("Sid", "#general");
        }
    }

    public static void main(String[] args) {
        Log.banner("5", "DATA CONSISTENCY");
        boolean pass = true;

        Log.section("Scenario 1: synchronous replication");
        Cluster one = new Cluster();
        one.setUp();
        one.primary.getState().sendMessage("Orion", "#general", "M1: hello");
        one.primary.getState().sendMessage("Sid", "#general", "M2: hi Orion");
        pass &= ConsistencyChecker.compareState(one.primary, one.backup);

        Log.section("Scenario 2: delayed replication, then the primary crashes (stale replica)");
        Cluster two = new Cluster();
        two.setUp();
        two.primary.getState().sendMessage("Orion", "#general", "M1: hello");
        two.link.setMode(ReplicationLink.Mode.DELAYED);
        two.primary.getState().sendMessage("Sid", "#general", "M2: hi Orion");
        boolean consistent = ConsistencyChecker.compareState(two.primary, two.backup);
        pass &= !consistent;       // the demo EXPECTS an inconsistency here
        two.primary.crash();
        int lost = two.link.dropPending();
        Log.info("BACKUP", "Takes over. Messages it can serve: ");
        for (Message m : two.backup.getState().getMessages("#general")) {
            Log.info("BACKUP", "   " + m);
        }
        Log.info("RESULT", lost + " update(s) were still in flight and are LOST: message M2 is gone for good");
        pass &= lost == 1 && two.backup.getState().messageCount() == 1;

        Log.section("Scenario 3: delayed replication, then synchronization (recovery)");
        Cluster three = new Cluster();
        three.setUp();
        three.primary.getState().sendMessage("Orion", "#general", "M1: hello");
        three.link.setMode(ReplicationLink.Mode.DELAYED);
        three.primary.getState().sendMessage("Sid", "#general", "M2: hi Orion");
        pass &= !ConsistencyChecker.compareState(three.primary, three.backup);
        int fetched = three.backup.catchUpFrom(three.primary);
        Log.info("REPLICA", "Caught up with " + fetched + " missing message(s)");
        pass &= ConsistencyChecker.compareState(three.primary, three.backup);

        Log.section("Fault tolerance vs data consistency");
        Log.info("INFO", "Fault tolerance  = the system survives a failure (a backup exists and takes over)");
        Log.info("INFO", "Data consistency = the replicas hold correct, compatible data (backup has M1 AND M2)");
        Log.info("INFO", "Scenario 2 had a backup (fault tolerance) but stale data (no consistency).");

        Log.section("Result");
        Log.info("RESULT", pass ? "PASS - consistent after sync, inconsistent while delayed, stale after crash"
                : "FAIL - unexpected replica state");
        System.exit(pass ? 0 : 1);
    }
}
