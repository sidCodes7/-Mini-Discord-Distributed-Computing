package exp2;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import minidiscord.common.Log;

/**
 * EXP 2 demo: WHY the server needs thread-safe structures.
 * Four threads share (a) a plain counter vs an AtomicLong and (b) an ArrayList vs a
 * ConcurrentLinkedQueue. The unsafe versions lose updates; the exact numbers differ every run.
 *   java exp2.RaceConditionDemo
 */
public class RaceConditionDemo {
    private static final int THREADS = 4;
    private static final int PER_THREAD = 250_000;

    private static int unsafeCounter = 0;
    private static final AtomicLong safeCounter = new AtomicLong();

    public static void main(String[] args) throws Exception {
        Log.banner("2", "RACE CONDITION vs THREAD SAFETY");
        int expected = THREADS * PER_THREAD;

        Log.section("Message id generator: int++ vs AtomicLong");
        runThreads(() -> {
            for (int i = 0; i < PER_THREAD; i++) {
                unsafeCounter++;                 // read-modify-write: NOT atomic
                safeCounter.incrementAndGet();   // one atomic step
            }
        });
        Log.info("RACE", "Expected " + expected + " ids, plain int counter = " + unsafeCounter
                + "  (lost " + (expected - unsafeCounter) + ")");
        Log.info("SAFE", "Expected " + expected + " ids, AtomicLong        = " + safeCounter.get()
                + "  (lost " + (expected - safeCounter.get()) + ")");

        Log.section("Message storage: ArrayList vs ConcurrentLinkedQueue");
        List<Integer> unsafeList = new ArrayList<>();
        Queue<Integer> safeQueue = new ConcurrentLinkedQueue<>();
        AtomicInteger crashes = new AtomicInteger();
        runThreads(() -> {
            for (int i = 0; i < 50_000; i++) {
                try {
                    unsafeList.add(i);
                } catch (RuntimeException e) {
                    crashes.incrementAndGet();   // ArrayList can even throw ArrayIndexOutOfBounds
                }
                safeQueue.add(i);
            }
        });
        int listExpected = THREADS * 50_000;
        Log.info("RACE", "Expected " + listExpected + " messages, ArrayList holds " + unsafeList.size()
                + "  (lost " + (listExpected - unsafeList.size()) + ", exceptions " + crashes.get() + ")");
        Log.info("SAFE", "Expected " + listExpected + " messages, ConcurrentLinkedQueue holds " + safeQueue.size()
                + "  (lost " + (listExpected - safeQueue.size()) + ")");

        Log.section("Conclusion");
        Log.info("INFO", "Shared state touched by several threads needs thread-safe structures.");
        Log.info("INFO", "Mini Discord uses AtomicLong (ids), ConcurrentHashMap (users, channels),");
        Log.info("INFO", "ConcurrentLinkedQueue (messages) and CopyOnWriteArraySet (members).");
    }

    private static void runThreads(Runnable work) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(work, "racer-" + (i + 1));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}
