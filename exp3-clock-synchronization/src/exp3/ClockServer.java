package exp3;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

import minidiscord.common.Log;
import minidiscord.common.TcpChatServer;

/**
 * A node that owns a SimulatedClock and answers over TCP:
 *   TIME          -> its local time in epoch milliseconds
 *   ADJUST delta  -> adds delta to its clock offset (used by the Berkeley coordinator)
 *
 * It is the "time server" of Cristian's algorithm and also a Berkeley participant.
 * oneWayLatencyMillis simulates network delay: the node waits that long before reading the
 * clock and again before replying, so a request/response really takes about 2 x latency.
 */
public class ClockServer {
    private final SimulatedClock clock;
    private final TcpChatServer server;
    private final String tag;

    public ClockServer(SimulatedClock clock, int port, long oneWayLatencyMillis) {
        this.clock = clock;
        this.tag = clock.getName().toUpperCase();
        this.server = new TcpChatServer("clock-" + clock.getName(), port, Executors.newCachedThreadPool(),
                (client, line) -> handle(line, oneWayLatencyMillis));
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public SimulatedClock getClock() {
        return clock;
    }

    private List<String> handle(String line, long latency) {
        String[] p = line.trim().split("\\s+");
        switch (p[0].toUpperCase()) {
            case "TIME": {
                Log.sleep(latency);              // request travelling to the node
                long time = clock.now();
                Log.sleep(latency);              // reply travelling back
                return List.of(String.valueOf(time));
            }
            case "ADJUST": {
                long delta = Long.parseLong(p[1]);
                clock.adjust(delta);
                Log.info(tag, "Adjustment " + signed(delta) + " ms applied, new clock "
                        + Log.time(clock.now()));
                return List.of("OK");
            }
            default:
                return List.of("ERR unknown command");
        }
    }

    public static String signed(long value) {
        return (value >= 0 ? "+" : "") + value;
    }
}
