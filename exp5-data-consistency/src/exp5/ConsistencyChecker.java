package exp5;

import java.util.ArrayList;
import java.util.List;

import minidiscord.common.Log;
import minidiscord.common.Message;

/** Compares two replicas and reports CONSISTENT or INCONSISTENT. */
public final class ConsistencyChecker {
    private ConsistencyChecker() {
    }

    /** @return true if both replicas hold exactly the same users, memberships and messages */
    public static boolean compareState(ReplicaNode primary, ReplicaNode backup) {
        boolean same = primary.getState().fingerprint().equals(backup.getState().fingerprint());
        Log.info("CONSISTENCY", primary.getName() + ": " + primary.getState().messageCount() + " messages, last id "
                + primary.getState().lastMessageId());
        Log.info("CONSISTENCY", backup.getName() + ": " + backup.getState().messageCount() + " messages, last id "
                + backup.getState().lastMessageId());
        if (same) {
            Log.info("CONSISTENCY", "Primary and Backup state match -> CONSISTENT");
        } else {
            List<Long> missing = missingIds(primary, backup);
            Log.info("CONSISTENCY", "Primary and Backup state differ -> INCONSISTENT"
                    + (missing.isEmpty() ? "" : " (backup is missing message id(s) " + missing + ")"));
        }
        return same;
    }

    private static List<Long> missingIds(ReplicaNode primary, ReplicaNode backup) {
        List<Long> missing = new ArrayList<>();
        for (String channel : primary.getState().getChannelNames()) {
            for (Message m : primary.getState().getMessages(channel)) {
                if (!backup.getState().getMessages(channel).stream().anyMatch(b -> b.getId() == m.getId())) {
                    missing.add(m.getId());
                }
            }
        }
        return missing;
    }
}
