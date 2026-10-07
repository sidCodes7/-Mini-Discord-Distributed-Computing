package exp7;

import minidiscord.common.ChatState;
import minidiscord.common.Log;

/**
 * One backend chat server as its own process (multi-terminal use). Each process has its OWN
 * chat state, so a user registered on one server is unknown to the others - the in-JVM demo
 * (Exp7Demo) shares one state instead.
 *   java exp7.BackendServerMain <name> <port> [workMillis]     e.g.  "Server 1" 5201 100
 */
public class BackendServerMain {
    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "Server 1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5201;
        long work = args.length > 2 ? Long.parseLong(args[2]) : 50;
        Log.banner("7", "BACKEND " + name.toUpperCase());
        new BackendServer(name, port, new ChatState(), work).start();
        Log.info(name.toUpperCase(), "Chat server on port " + port + " (simulated work " + work + " ms per request)");
        Thread.currentThread().join();
    }
}
