package exp1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import minidiscord.common.ChatException;
import minidiscord.common.Log;
import minidiscord.common.Message;

/**
 * EXP 1 client.
 *   java exp1.RmiChatClient Orion demo          scripted scenario
 *   java exp1.RmiChatClient Orion interactive   type commands yourself
 * Optional third argument: registry port (default 1099).
 */
public class RmiChatClient {
    public static void main(String[] args) throws Exception {
        String user = args.length > 0 ? args[0] : "Orion";
        String mode = args.length > 1 ? args[1] : "demo";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : RmiChatServer.REGISTRY_PORT;

        Log.banner("1", "RPC / JAVA RMI (CLIENT " + user + ")");
        ChatService chat;
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", port);
            registry.list(); // forces a real connection so a missing server is detected now
            Log.info("CLIENT", "Connected to RMI Registry");
            chat = (ChatService) registry.lookup(RmiChatServer.SERVICE_NAME);
            Log.info("CLIENT", "Remote object located");
        } catch (RemoteException | NotBoundException e) {
            Log.info("CLIENT", "RMI server unavailable: " + e.getMessage() + " (start RmiChatServer first)");
            return;
        }

        try {
            if (mode.equalsIgnoreCase("interactive")) {
                interactive(chat);
            } else {
                demo(chat, user);
            }
        } catch (RemoteException e) {
            Log.info("CLIENT", "Remote call failed (server down?): " + e.getMessage());
        }
    }

    private static void demo(ChatService chat, String user) throws RemoteException {
        Log.info("CLIENT", "The object I hold is a stub (proxy): " + Proxy.isProxyClass(chat.getClass())
                + " -> " + chat.getClass().getSimpleName());
        Log.info("CLIENT", "My JVM:     pid=" + ProcessHandle.current().pid());
        Log.info("CLIENT", "Server JVM: " + chat.whereAmI() + "   <- the methods run THERE");

        Log.section("Remote method invocations");
        attempt(() -> {
            Log.info("CLIENT", "Calling registerUser(" + user + ")");
            chat.registerUser(user);
        });
        attempt(() -> {
            Log.info("CLIENT", "Calling joinChannel(" + user + ", #general)");
            chat.joinChannel(user, "#general");
        });
        attempt(() -> {
            Log.info("CLIENT", "Calling sendMessage(" + user + ", #general, ...)");
            Message m = chat.sendMessage(user, "#general", "Hello over RMI from " + user);
            Log.info("CLIENT", "Server returned a serialized Message object: " + m);
        });
        attempt(() -> {
            Log.info("CLIENT", "Calling getMessages(#general, 0)");
            List<Message> messages = chat.getMessages("#general", 0);
            Log.info("CLIENT", messages.size() + " message(s) received from the server:");
            for (Message m : messages) {
                Log.info("CLIENT", "   " + m);
            }
        });
        attempt(() -> {
            Log.info("CLIENT", "Calling getOnlineUsers()");
            Log.info("CLIENT", "Online users: " + chat.getOnlineUsers());
        });

        Log.section("Errors raised on the server travel back to the client");
        attempt(() -> chat.sendMessage(user, "#nowhere", "this channel does not exist"));
        attempt(() -> chat.registerUser(user));
    }

    private static void interactive(ChatService chat) throws Exception {
        Log.info("CLIENT", "Commands: register U | login U | join U #c | leave U #c | send U #c text | get #c | users | quit");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null && !line.trim().equalsIgnoreCase("quit")) {
            String[] p = line.trim().split("\\s+", 4);
            try {
                switch (p[0].toLowerCase()) {
                    case "register" -> chat.registerUser(p[1]);
                    case "login" -> chat.login(p[1]);
                    case "join" -> chat.joinChannel(p[1], p[2]);
                    case "leave" -> chat.leaveChannel(p[1], p[2]);
                    case "send" -> Log.info("CLIENT", String.valueOf(chat.sendMessage(p[1], p[2], p[3])));
                    case "get" -> chat.getMessages(p[1], 0).forEach(m -> Log.info("CLIENT", m.toString()));
                    case "users" -> Log.info("CLIENT", String.valueOf(chat.getOnlineUsers()));
                    default -> Log.info("CLIENT", "Unknown command");
                }
            } catch (ChatException e) {
                Log.info("CLIENT", "Server says: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.info("CLIENT", "Missing argument");
            }
        }
    }

    private interface RemoteAction {
        void run() throws RemoteException;
    }

    private static void attempt(RemoteAction action) throws RemoteException {
        try {
            action.run();
        } catch (ChatException e) {
            Log.info("CLIENT", "Server rejected the call: " + e.getMessage());
        }
    }
}
