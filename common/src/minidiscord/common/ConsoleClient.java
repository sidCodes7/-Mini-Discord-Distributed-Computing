package minidiscord.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/** Shared console helpers for the socket based clients (print requests/responses, interactive loop). */
public final class ConsoleClient {
    private ConsoleClient() {
    }

    /** Sends one request somewhere (plain connection, failover client, ...). */
    public interface Requester {
        List<String> request(String line) throws IOException;
    }

    /** Print the request, send it, print the response; returns the response. */
    public static List<String> call(String tag, Requester requester, String line) throws IOException {
        Log.info(tag, "> " + line);
        List<String> response = requester.request(line);
        print(tag, response);
        return response;
    }

    public static void print(String tag, List<String> response) {
        for (int i = 0; i < response.size(); i++) {
            String line = response.get(i);
            if (i > 0 && line.contains("|")) {
                try {
                    line = "   " + Message.fromWireLine(line);
                } catch (RuntimeException ignored) {
                    // not a message line, print as is
                }
            }
            Log.info(tag, line);
        }
    }

    /** Read commands from the keyboard until "quit" or end of input. */
    public static void interactive(String tag, Requester requester) throws IOException {
        Log.info(tag, "Type commands (REGISTER, LOGIN, JOIN, LEAVE, SEND, GET, USERS, CHANNELS) or 'quit'.");
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = keyboard.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.trim().equalsIgnoreCase("quit")) {
                break;
            }
            try {
                print(tag, requester.request(line));
            } catch (IOException e) {
                Log.info(tag, "Request failed: " + e.getMessage());
                break;
            }
        }
    }
}
