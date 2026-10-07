package exp3;

import java.io.IOException;

import minidiscord.common.ChatConnection;
import minidiscord.common.Log;

/**
 * Cristian's algorithm.
 *   1. note local time t0, ask the time server for its time
 *   2. server answers with Ts; note local time t1 when the answer arrives
 *   3. round trip time RTT = t1 - t0; assume the answer needed RTT/2 to travel
 *   4. best estimate of the true time now = Ts + RTT/2
 *   5. adjustment = estimate - t1   (added to the local clock)
 */
public final class CristianClient {
    private CristianClient() {
    }

    /** @return the adjustment that was applied to the clock, in milliseconds */
    public static long synchronize(SimulatedClock clock, String host, int port) throws IOException {
        try (ChatConnection connection = new ChatConnection(host, port, 2000)) {
            long t0 = clock.now();
            long serverTime = Long.parseLong(connection.request("TIME").get(0).trim());
            long t1 = clock.now();

            long roundTrip = t1 - t0;
            long estimatedDelay = roundTrip / 2;
            long estimatedServerNow = serverTime + estimatedDelay;
            long adjustment = estimatedServerNow - t1;
            long before = clock.now();
            clock.adjust(adjustment);

            String tag = "CLOCK " + clock.getName();
            Log.info(tag, "CRISTIAN'S ALGORITHM");
            Log.info(tag, "Client local time (t0) : " + Log.time(t0));
            Log.info(tag, "Server time (Ts)       : " + Log.time(serverTime));
            Log.info(tag, "Round trip time        : " + roundTrip + " ms");
            Log.info(tag, "Estimated network delay: " + estimatedDelay + " ms (RTT / 2)");
            Log.info(tag, "Calculated adjustment  : " + ClockServer.signed(adjustment)
                    + " ms (Ts + delay - t1)");
            Log.info(tag, "Client time before     : " + Log.time(before));
            Log.info(tag, "Corrected client time  : " + Log.time(clock.now()));
            return adjustment;
        }
    }
}
