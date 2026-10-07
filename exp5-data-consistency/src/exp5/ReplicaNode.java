package exp5;

import java.util.ArrayList;
import java.util.List;

import minidiscord.common.ChatState;
import minidiscord.common.Log;

/** One server holding its own complete copy of the chat state (primary or backup). */
public class ReplicaNode {
    private final String name;
    private final ChatState state = new ChatState();
    private boolean crashed;

    public ReplicaNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ChatState getState() {
        return state;
    }

    public boolean isCrashed() {
        return crashed;
    }

    public void crash() {
        crashed = true;
        Log.info(name, "CRASHED");
    }

    /** Receive one replicated update from the primary. */
    public void applyUpdate(String op) {
        state.applyOp(op);
        Log.info(name, "Update received: " + ChatState.describeOp(op));
    }

    /**
     * Synchronization: ask a healthy node for everything newer than what we have. Users and
     * memberships are re-applied (harmless duplicates are ignored); messages only above our last id.
     */
    public int catchUpFrom(ReplicaNode source) {
        long last = state.lastMessageId();
        Log.info("REPLICA", name + " has last message id " + last + ", requesting newer data from " + source.getName());
        List<String> missing = new ArrayList<>();
        for (String op : source.getState().snapshotOps()) {
            if (op.startsWith("MSG|")) {
                if (Long.parseLong(op.split("\\|", 3)[1]) > last) {
                    missing.add(op);
                }
            } else {
                missing.add(op);
            }
        }
        int messages = 0;
        for (String op : missing) {
            state.applyOp(op);
            if (op.startsWith("MSG|")) {
                messages++;
                Log.info("REPLICA", name + " caught up: " + ChatState.describeOp(op));
            }
        }
        return messages;
    }
}
