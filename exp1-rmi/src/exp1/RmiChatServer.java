package exp1;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import minidiscord.common.ChatState;
import minidiscord.common.Log;

/**
 * EXP 1 server: create the RMI registry, create the remote object, bind it under a name, wait.
 *   java exp1.RmiChatServer [registryPort]
 */
public class RmiChatServer {
    public static final int REGISTRY_PORT = 1099;
    public static final String SERVICE_NAME = "MiniDiscordChat";

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : REGISTRY_PORT;
        // Make stubs point to localhost instead of whatever address the machine guesses for itself.
        System.setProperty("java.rmi.server.hostname", "localhost");

        Log.banner("1", "RPC / JAVA RMI (SERVER)");
        Registry registry = LocateRegistry.createRegistry(port);
        Log.info("SERVER", "RMI Registry started on port " + port);

        ChatServiceImpl service = new ChatServiceImpl(new ChatState());
        registry.rebind(SERVICE_NAME, service);
        Log.info("SERVER", SERVICE_NAME + " bound");
        Log.info("SERVER", "Waiting for clients (JVM pid " + ProcessHandle.current().pid() + ")");
        Thread.currentThread().join();
    }
}
