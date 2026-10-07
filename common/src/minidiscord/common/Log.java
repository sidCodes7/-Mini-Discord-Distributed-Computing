package minidiscord.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small console helper so every experiment prints in the same style. */
public final class Log {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private Log() {
    }

    public static void banner(String expNo, String title) {
        String line = "========================================";
        System.out.println(line);
        System.out.println("MINI DISCORD");
        System.out.println("EXP " + expNo + " - " + title);
        System.out.println(line);
    }

    public static void section(String title) {
        System.out.println();
        System.out.println("---------- " + title + " ----------");
    }

    public static void info(String tag, String message) {
        System.out.println("[" + tag + "] " + message);
    }

    /** Formats epoch milliseconds as HH:mm:ss.SSS (local zone). */
    public static String time(long epochMillis) {
        return TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
