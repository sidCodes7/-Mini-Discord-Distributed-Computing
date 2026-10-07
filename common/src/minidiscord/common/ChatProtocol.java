package minidiscord.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny line-based text protocol used by the socket based experiments (0, 2, 4, 7).
 *
 *   REGISTER user | LOGIN user | LOGOUT user
 *   JOIN user #channel | LEAVE user #channel
 *   SEND user #channel text...
 *   GET #channel [afterId]       -> "OK n" followed by n lines: id|timestamp|sender|channel|content
 *   USERS | CHANNELS | PING
 *
 * Every other command gets one answer line starting with "OK " or "ERR ".
 */
public final class ChatProtocol {
    private ChatProtocol() {
    }

    public static List<String> handle(String line, ChatState state) {
        try {
            String[] p = line.trim().split("\\s+", 4);
            String command = p[0].toUpperCase();
            switch (command) {
                case "PING":
                    return ok("PONG");
                case "REGISTER":
                    need(p, 2, "REGISTER user");
                    state.registerUser(p[1]);
                    return ok("Registered " + p[1]);
                case "LOGIN":
                    need(p, 2, "LOGIN user");
                    state.login(p[1]);
                    return ok(p[1] + " is online");
                case "LOGOUT":
                    need(p, 2, "LOGOUT user");
                    state.logout(p[1]);
                    return ok(p[1] + " is offline");
                case "JOIN":
                    need(p, 3, "JOIN user #channel");
                    state.joinChannel(p[1], p[2]);
                    return ok(p[1] + " joined " + p[2]);
                case "LEAVE":
                    need(p, 3, "LEAVE user #channel");
                    state.leaveChannel(p[1], p[2]);
                    return ok(p[1] + " left " + p[2]);
                case "SEND":
                    need(p, 3, "SEND user #channel text");
                    if (p.length < 4) {
                        throw new ChatException("Message must not be empty");
                    }
                    Message m = state.sendMessage(p[1], p[2], p[3]);
                    return ok("Message " + m.getId() + " stored in " + m.getChannel());
                case "GET":
                    need(p, 2, "GET #channel [afterId]");
                    long after = p.length > 2 ? Long.parseLong(p[2].trim()) : 0;
                    List<Message> messages = state.getMessages(p[1], after);
                    List<String> response = new ArrayList<>();
                    response.add("OK " + messages.size());
                    for (Message msg : messages) {
                        response.add(msg.toWireLine());
                    }
                    return response;
                case "USERS":
                    return ok(String.join(",", state.getOnlineUsers()));
                case "CHANNELS":
                    return ok(String.join(",", state.getChannelNames()));
                default:
                    return err("Unknown command '" + p[0] + "'");
            }
        } catch (ChatException e) {
            return err(e.getMessage());
        } catch (NumberFormatException e) {
            return err("Invalid number in request");
        }
    }

    private static void need(String[] parts, int count, String usage) {
        if (parts.length < count) {
            throw new ChatException("Usage: " + usage);
        }
    }

    public static List<String> ok(String text) {
        List<String> r = new ArrayList<>();
        r.add("OK " + text);
        return r;
    }

    public static List<String> err(String text) {
        List<String> r = new ArrayList<>();
        r.add("ERR " + text);
        return r;
    }
}
