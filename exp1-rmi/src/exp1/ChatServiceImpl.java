package exp1;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.Message;

/**
 * The REMOTE IMPLEMENTATION. Extending UnicastRemoteObject exports the object, so RMI creates
 * a listening endpoint for it and clients receive a stub (proxy) that forwards calls here.
 */
public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {
    private static final long serialVersionUID = 1L;

    private final transient ChatState state;

    public ChatServiceImpl(ChatState state) throws RemoteException {
        super();
        this.state = state;
    }

    @Override
    public void registerUser(String user) {
        Log.info("SERVER", "Remote registerUser(" + user + ") received");
        state.registerUser(user);
    }

    @Override
    public void login(String user) {
        Log.info("SERVER", "Remote login(" + user + ") received");
        state.login(user);
    }

    @Override
    public void joinChannel(String user, String channel) {
        Log.info("SERVER", "Remote joinChannel(" + user + ", " + channel + ") received");
        state.joinChannel(user, channel);
    }

    @Override
    public void leaveChannel(String user, String channel) {
        Log.info("SERVER", "Remote leaveChannel(" + user + ", " + channel + ") received");
        state.leaveChannel(user, channel);
    }

    @Override
    public Message sendMessage(String user, String channel, String content) {
        Log.info("SERVER", "Remote sendMessage(" + user + ", " + channel + ", \"" + content + "\") received");
        return state.sendMessage(user, channel, content);
    }

    @Override
    public List<Message> getMessages(String channel, long afterId) {
        Log.info("SERVER", "Remote getMessages(" + channel + ", " + afterId + ") received");
        return state.getMessages(channel, afterId);
    }

    @Override
    public List<String> getOnlineUsers() {
        Log.info("SERVER", "Remote getOnlineUsers() received");
        return state.getOnlineUsers();
    }

    @Override
    public String whereAmI() {
        return "JVM pid=" + ProcessHandle.current().pid() + ", thread=" + Thread.currentThread().getName();
    }
}
