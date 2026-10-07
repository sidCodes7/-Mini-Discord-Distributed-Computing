package exp5;

import java.util.ArrayDeque;
import java.util.Deque;

import minidiscord.common.ChatState;
import minidiscord.common.Log;

/**
 * The channel that carries every primary update to the backup.
 *   SYNCHRONOUS  - the update reaches the backup before the client is answered (strong consistency)
 *   DELAYED      - the update waits in a network queue (asynchronous replication with lag)
 */
public class ReplicationLink {
    public enum Mode { SYNCHRONOUS, DELAYED }

    private final ReplicaNode backup;
    private final Deque<String> inFlight = new ArrayDeque<>();
    private Mode mode = Mode.SYNCHRONOUS;

    public ReplicationLink(ReplicaNode backup) {
        this.backup = backup;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        Log.info("REPLICATION", "Mode is now " + mode);
    }

    /** Called for every state change made on the primary. */
    public void replicate(String op) {
        Log.info("REPLICATION", "Sending update to backup: " + ChatState.describeOp(op));
        if (mode == Mode.SYNCHRONOUS) {
            backup.applyUpdate(op);
        } else {
            inFlight.add(op);
            Log.info("REPLICATION", "Update delayed in the network queue (" + inFlight.size() + " waiting)");
        }
    }

    public int pendingUpdates() {
        return inFlight.size();
    }

    /** The delayed updates finally arrive. */
    public void deliverPending() {
        Log.info("REPLICATION", "Delivering " + inFlight.size() + " delayed update(s)");
        while (!inFlight.isEmpty()) {
            backup.applyUpdate(inFlight.poll());
        }
    }

    /** The primary died: whatever was still in the queue is gone. */
    public int dropPending() {
        int lost = inFlight.size();
        inFlight.clear();
        return lost;
    }
}
