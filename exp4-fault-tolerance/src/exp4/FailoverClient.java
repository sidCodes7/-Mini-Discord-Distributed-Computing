package exp4;

import java.io.IOException;
import java.util.List;

import minidiscord.common.ChatConnection;
import minidiscord.common.Log;

/**
 * A client that knows two servers. It talks to the first one that answers; when the
 * connection breaks it keeps trying the list (primary, then backup) until one is reachable.
 */
public class FailoverClient implements AutoCloseable {
    private static final long GIVE_UP_AFTER_MILLIS = 20_000;

    private final String[] labels;
    private final int[] ports;
    private final String tag;
    private ChatConnection connection;
    private int connectedTo = -1;

    public FailoverClient(String tag, String[] labels, int[] ports) {
        this.tag = tag;
        this.labels = labels;
        this.ports = ports;
    }

    public List<String> request(String line) throws IOException {
        long deadline = System.currentTimeMillis() + GIVE_UP_AFTER_MILLIS;
        while (true) {
            try {
                if (connection == null) {
                    connect(deadline);
                }
                return connection.request(line);
            } catch (IOException e) {
                dropConnection();
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException("No server reachable: " + e.getMessage());
                }
                Log.info(tag, "Connection to " + (connectedTo >= 0 ? labels[connectedTo] : "server")
                        + " lost (" + e.getMessage() + "), looking for another server...");
                connectedTo = -1;
            }
        }
    }

    private void connect(long deadline) throws IOException {
        boolean reported = false;
        while (System.currentTimeMillis() < deadline) {
            for (int i = 0; i < ports.length; i++) {
                try {
                    connection = new ChatConnection("localhost", ports[i], 400);
                    connectedTo = i;
                    Log.info(tag, "Connected to " + labels[i] + " (port " + ports[i] + ")");
                    return;
                } catch (IOException e) {
                    Log.info(tag, labels[i] + " (port " + ports[i] + ") is not reachable");
                }
            }
            if (!reported) {
                Log.info(tag, "No server answering yet, retrying (a standby needs a moment to take over)");
                reported = true;
            }
            Log.sleep(500);
        }
        throw new IOException("no server reachable");
    }

    private void dropConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @Override
    public void close() {
        dropConnection();
    }
}
