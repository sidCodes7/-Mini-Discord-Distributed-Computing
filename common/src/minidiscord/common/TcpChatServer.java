package minidiscord.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * A small multi-client TCP server. One accept thread hands every connection to the given
 * ExecutorService; the worker reads request lines and asks a RequestHandler for the answer.
 * stop() closes the listening socket and every client socket, which is how experiments
 * simulate a server crash.
 */
public class TcpChatServer {

    /** Turns one request line into the response lines. Runs on a worker thread. */
    public interface RequestHandler {
        List<String> handle(String clientDescription, String requestLine);
    }

    private final String name;
    private final int port;
    private final ExecutorService workers;
    private final RequestHandler handler;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public TcpChatServer(String name, int port, ExecutorService workers, RequestHandler handler) {
        this.name = name;
        this.port = port;
        this.workers = workers;
        this.handler = handler;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, name + "-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                workers.submit(() -> serve(socket));
            } catch (IOException e) {
                if (running) {
                    Log.info(name, "accept failed: " + e.getMessage());
                }
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        String client = String.valueOf(socket.getRemoteSocketAddress());
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String line;
            while (running && (line = in.readLine()) != null) {
                if (line.trim().equalsIgnoreCase("QUIT")) {
                    break;
                }
                for (String responseLine : handler.handle(client, line)) {
                    out.println(responseLine);
                }
            }
        } catch (IOException ignored) {
            // client disconnected or server stopped
        } finally {
            clients.remove(socket);
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    /** Stop listening and drop every connection (simulates a crash for the clients). */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        for (Socket s : clients) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        clients.clear();
        workers.shutdownNow();
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }
}
