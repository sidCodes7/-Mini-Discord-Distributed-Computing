package exp3;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A node's local clock = the real system clock + a configurable offset (the "drift").
 * Synchronization algorithms correct the node by calling adjust(); the offset really changes.
 */
public class SimulatedClock {
    private final String name;
    private final AtomicLong offsetMillis;

    public SimulatedClock(String name, long offsetMillis) {
        this.name = name;
        this.offsetMillis = new AtomicLong(offsetMillis);
    }

    public String getName() {
        return name;
    }

    /** Local time of this node in epoch milliseconds. */
    public long now() {
        return System.currentTimeMillis() + offsetMillis.get();
    }

    public void adjust(long deltaMillis) {
        offsetMillis.addAndGet(deltaMillis);
    }

    public long getOffset() {
        return offsetMillis.get();
    }
}
