package minidiscord.common;

/** Thrown when a chat operation is invalid (unknown channel, duplicate user, empty message, ...). */
public class ChatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChatException(String message) {
        super(message);
    }
}
