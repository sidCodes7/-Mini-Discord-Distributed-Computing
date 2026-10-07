package minidiscord.common;

import java.io.Serializable;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The complete state of one Mini Discord server: users, channels, membership and messages.
 *
 * Thread safety comes from thread-safe collections, not from locking every method:
 *  - ConcurrentHashMap            users and channels
 *  - CopyOnWriteArraySet          channel members (inside Channel)
 *  - ConcurrentLinkedQueue        messages (inside Channel)
 *  - AtomicLong                   unique, increasing message ids
 *
 * Every successful change is also described as a one-line "operation" (REGISTER|Orion,
 * MSG|1|...). An optional update listener receives these lines; replication (Exp 4/5)
 * uses them to copy state to a backup. applyOp() replays such a line on another replica.
 */
public class ChatState implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final List<String> DEFAULT_CHANNELS = List.of("#general", "#coding", "#random");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{2,20}");
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();
    private transient volatile Consumer<String> updateListener;

    public ChatState() {
        createDefaultChannels();
    }

    private void createDefaultChannels() {
        for (String name : DEFAULT_CHANNELS) {
            channels.put(name, new Channel(name));
        }
    }

    /** Register a callback that receives one operation line per successful state change. */
    public void setUpdateListener(Consumer<String> listener) {
        this.updateListener = listener;
    }

    private void publish(String op) {
        Consumer<String> listener = updateListener;
        if (listener != null) {
            listener.accept(op);
        }
    }

    // ------------------------------------------------------------------ client operations

    public void registerUser(String name) {
        validateName(name);
        User user = new User(name);
        if (users.putIfAbsent(name, user) != null) {
            throw new ChatException("User '" + name + "' is already registered");
        }
        user.setOnline(true);
        publish("REGISTER|" + name);
    }

    public void login(String name) {
        requireUser(name).setOnline(true);
        publish("LOGIN|" + name);
    }

    public void logout(String name) {
        requireUser(name).setOnline(false);
        publish("LOGOUT|" + name);
    }

    public void joinChannel(String user, String channel) {
        requireUser(user);
        if (!requireChannel(channel).addMember(user)) {
            throw new ChatException(user + " is already a member of " + channel);
        }
        publish("JOIN|" + user + "|" + channel);
    }

    public void leaveChannel(String user, String channel) {
        requireUser(user);
        if (!requireChannel(channel).removeMember(user)) {
            throw new ChatException(user + " is not a member of " + channel);
        }
        publish("LEAVE|" + user + "|" + channel);
    }

    public Message sendMessage(String sender, String channel, String content) {
        return sendMessage(sender, channel, content, System.currentTimeMillis());
    }

    /** Same as above, but the caller supplies the timestamp (e.g. from a synchronized clock). */
    public Message sendMessage(String sender, String channel, String content, long timestamp) {
        requireUser(sender);
        Channel ch = requireChannel(channel);
        if (content == null || content.trim().isEmpty()) {
            throw new ChatException("Message must not be empty");
        }
        String clean = content.replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            throw new ChatException("Message is longer than " + MAX_MESSAGE_LENGTH + " characters");
        }
        if (!ch.isMember(sender)) {
            throw new ChatException(sender + " must join " + channel + " before sending");
        }
        long id = idGenerator.incrementAndGet();
        Message message = new Message(id, sender, channel, clean, timestamp);
        ch.addMessage(message);
        publish("MSG|" + id + "|" + timestamp + "|" + sender + "|" + channel + "|"
                + URLEncoder.encode(clean, StandardCharsets.UTF_8));
        return message;
    }

    public List<Message> getMessages(String channel) {
        return getMessages(channel, 0);
    }

    public List<Message> getMessages(String channel, long afterId) {
        return requireChannel(channel).history(afterId);
    }

    public List<String> getOnlineUsers() {
        List<String> online = new ArrayList<>();
        for (User u : users.values()) {
            if (u.isOnline()) {
                online.add(u.getName());
            }
        }
        online.sort(String::compareTo);
        return online;
    }

    public List<String> getRegisteredUsers() {
        List<String> names = new ArrayList<>(users.keySet());
        names.sort(String::compareTo);
        return names;
    }

    public List<String> getMembers(String channel) {
        return requireChannel(channel).memberNames();
    }

    public List<String> getChannelNames() {
        List<String> names = new ArrayList<>(channels.keySet());
        names.sort(String::compareTo);
        return names;
    }

    public long lastMessageId() {
        return idGenerator.get();
    }

    public int messageCount() {
        int total = 0;
        for (Channel c : channels.values()) {
            total += c.history(0).size();
        }
        return total;
    }

    // ------------------------------------------------------------------ replication support

    /** Replay one operation line produced by another replica. Idempotent for duplicates. */
    public void applyOp(String op) {
        String[] p = op.split("\\|", 6);
        switch (p[0]) {
            case "REGISTER" -> ensureUser(p[1]).setOnline(true);
            case "LOGIN" -> ensureUser(p[1]).setOnline(true);
            case "LOGOUT" -> ensureUser(p[1]).setOnline(false);
            case "JOIN" -> {
                ensureUser(p[1]);
                requireChannel(p[2]).addMember(p[1]);
            }
            case "LEAVE" -> requireChannel(p[2]).removeMember(p[1]);
            case "MSG" -> {
                long id = Long.parseLong(p[1]);
                long timestamp = Long.parseLong(p[2]);
                Channel ch = requireChannel(p[4]);
                if (!ch.hasMessage(id)) {
                    ch.addMessage(new Message(id, p[3], p[4],
                            URLDecoder.decode(p[5], StandardCharsets.UTF_8), timestamp));
                }
                idGenerator.accumulateAndGet(id, Math::max);
            }
            default -> throw new ChatException("Unknown replication operation: " + op);
        }
    }

    /** Short human readable form of an operation line, e.g. "MSG id=3 from Orion in #general". */
    public static String describeOp(String op) {
        String[] p = op.split("\\|", 6);
        switch (p[0]) {
            case "MSG":
                return "MSG id=" + p[1] + " from " + p[3] + " in " + p[4];
            case "JOIN":
            case "LEAVE":
                return p[0] + " " + p[1] + " " + p[2];
            default:
                return p[0] + " " + p[1];
        }
    }

    /** The whole state as a list of operation lines (used to bring a new replica up to date). */
    public List<String> snapshotOps() {
        List<String> ops = new ArrayList<>();
        for (String name : getRegisteredUsers()) {
            ops.add("REGISTER|" + name);
            if (!users.get(name).isOnline()) {
                ops.add("LOGOUT|" + name);
            }
        }
        List<Message> all = new ArrayList<>();
        for (String channelName : getChannelNames()) {
            for (String member : channels.get(channelName).memberNames()) {
                ops.add("JOIN|" + member + "|" + channelName);
            }
            all.addAll(channels.get(channelName).history(0));
        }
        all.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        for (Message m : all) {
            ops.add("MSG|" + m.getId() + "|" + m.getTimestamp() + "|" + m.getSender() + "|"
                    + m.getChannel() + "|" + URLEncoder.encode(m.getContent(), StandardCharsets.UTF_8));
        }
        return ops;
    }

    /** Forget everything (a replica does this before loading a fresh snapshot). */
    public void reset() {
        users.clear();
        channels.clear();
        idGenerator.set(0);
        createDefaultChannels();
    }

    /** Canonical text of users, memberships and messages; equal text means equal replicas. */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append("users=").append(getRegisteredUsers()).append('\n');
        for (String name : getChannelNames()) {
            Channel ch = channels.get(name);
            sb.append(name).append(" members=").append(ch.memberNames()).append('\n');
            for (Message m : ch.history(0)) {
                sb.append("  ").append(m.getId()).append('|').append(m.getSender()).append('|')
                        .append(m.getContent()).append('\n');
            }
        }
        sb.append("lastMessageId=").append(lastMessageId());
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static void validateName(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new ChatException("Invalid username '" + name + "' (use 2-20 letters, digits or _)");
        }
    }

    private User requireUser(String name) {
        User user = name == null ? null : users.get(name);
        if (user == null) {
            throw new ChatException("Unknown user '" + name + "' (register first)");
        }
        return user;
    }

    private User ensureUser(String name) {
        return users.computeIfAbsent(name, User::new);
    }

    private Channel requireChannel(String name) {
        Channel ch = name == null ? null : channels.get(name);
        if (ch == null) {
            throw new ChatException("Unknown channel '" + name + "'");
        }
        return ch;
    }
}
