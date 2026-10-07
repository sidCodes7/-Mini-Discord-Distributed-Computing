package minidiscord.common;

import java.io.Serializable;

/** One chat message. Serializable so it can cross JVM boundaries (RMI). */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String sender;
    private final String channel;
    private final String content;
    private final long timestamp;

    public Message(long id, String sender, String channel, String content, long timestamp) {
        this.id = id;
        this.sender = sender;
        this.channel = channel;
        this.content = content;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getChannel() {
        return channel;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** Wire format used by the text protocol: id|timestamp|sender|channel|content */
    public String toWireLine() {
        return id + "|" + timestamp + "|" + sender + "|" + channel + "|" + content;
    }

    public static Message fromWireLine(String line) {
        String[] p = line.split("\\|", 5);
        if (p.length < 5) {
            throw new IllegalArgumentException("Bad message line: " + line);
        }
        return new Message(Long.parseLong(p[0]), p[2], p[3], p[4], Long.parseLong(p[1]));
    }

    @Override
    public String toString() {
        return "#" + id + " [" + Log.time(timestamp) + "] " + sender + " in " + channel + ": " + content;
    }
}
