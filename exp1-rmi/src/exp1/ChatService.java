package exp1;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import minidiscord.common.Message;

/**
 * The REMOTE INTERFACE. Only methods declared here can be called from another JVM, and every
 * one of them must declare RemoteException (the network can fail at any call).
 * Arguments and return values (String, Message, List) must be Serializable.
 */
public interface ChatService extends Remote {
    void registerUser(String user) throws RemoteException;

    void login(String user) throws RemoteException;

    void joinChannel(String user, String channel) throws RemoteException;

    void leaveChannel(String user, String channel) throws RemoteException;

    Message sendMessage(String user, String channel, String content) throws RemoteException;

    List<Message> getMessages(String channel, long afterId) throws RemoteException;

    List<String> getOnlineUsers() throws RemoteException;

    /** Describes the JVM that actually executes the call - proves the code runs on the server. */
    String whereAmI() throws RemoteException;
}
