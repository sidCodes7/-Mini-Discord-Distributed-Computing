package minidiscord.common;

import java.io.Serializable;

/** A registered chat user. */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final long registeredAt;
    private volatile boolean online;

    public User(String name) {
        this.name = name;
        this.registeredAt = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
