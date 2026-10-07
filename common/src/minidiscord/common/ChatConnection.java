package minidiscord.common;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Client side of the text protocol: one TCP connection, request/response. */
public class ChatConnection implements Closeable {
    private final Socket socket = new Socket();
    private final BufferedReader in;
    private final PrintWriter out;

    public ChatConnection(String host, int port, int connectTimeoutMillis) throws IOException {
        try {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(10_000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** Send one request line and read the complete response (GET answers are multi-line). */
    public List<String> request(String line) throws IOException {
        out.println(line);
        if (out.checkError()) {
            throw new IOException("Could not write to server");
        }
        String first = in.readLine();
        if (first == null) {
            throw new IOException("Connection closed by server");
        }
        List<String> response = new ArrayList<>();
        response.add(first);
        if (line.trim().toUpperCase().startsWith("GET") && first.startsWith("OK ")) {
            int count = Integer.parseInt(first.substring(3).trim());
            for (int i = 0; i < count; i++) {
                String next = in.readLine();
                if (next == null) {
                    throw new IOException("Connection closed while reading messages");
                }
                response.add(next);
            }
        }
        return response;
    }

    @Override
    public void close() {
        try {
            out.println("QUIT");
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
