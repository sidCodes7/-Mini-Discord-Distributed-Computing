package minidiscord.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

/** A chat channel: thread-safe member set and message queue. */
public class Channel implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final Set<String> members = new CopyOnWriteArraySet<>();
    private final Queue<Message> messages = new ConcurrentLinkedQueue<>();

    public Channel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** @return false if the user was already a member */
    public boolean addMember(String user) {
        return members.add(user);
    }

    /** @return false if the user was not a member */
    public boolean removeMember(String user) {
        return members.remove(user);
    }

    public boolean isMember(String user) {
        return members.contains(user);
    }

    public List<String> memberNames() {
        List<String> names = new ArrayList<>(members);
        names.sort(String::compareTo);
        return names;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public boolean hasMessage(long id) {
        for (Message m : messages) {
            if (m.getId() == id) {
                return true;
            }
        }
        return false;
    }

    /** Messages with id greater than afterId, ordered by id. */
    public List<Message> history(long afterId) {
        List<Message> result = new ArrayList<>();
        for (Message m : messages) {
            if (m.getId() > afterId) {
                result.add(m);
            }
        }
        result.sort(Comparator.comparingLong(Message::getId));
        return result;
    }
}
