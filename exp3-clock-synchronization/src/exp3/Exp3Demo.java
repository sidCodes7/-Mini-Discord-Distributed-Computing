package exp3;

import java.util.ArrayList;
import java.util.List;

import minidiscord.common.ChatState;
import minidiscord.common.Log;
import minidiscord.common.Message;

/**
 * EXP 3 demo, all parts in one JVM but talking over real TCP sockets:
 *   A. clock drift     - three nodes with different offsets
 *   B. Cristian        - drifting nodes synchronize with a time server
 *   C. Berkeley        - a coordinator averages the clocks of four nodes
 *   D. Mini Discord    - a chat message stamped with the synchronized clock
 *   java exp3.Exp3Demo
 */
public class Exp3Demo {
    private static final long ALLOWED_ERROR_MILLIS = 40;

    public static void main(String[] args) throws Exception {
        Log.banner("3", "CLOCK SYNCHRONIZATION");
        boolean pass = true;

        // ------------------------------------------------------------------ A. drift
        Log.section("A. CLOCK DRIFT");
        SimulatedClock nodeA = new SimulatedClock("Node A", +5000);
        SimulatedClock nodeB = new SimulatedClock("Node B", -3000);
        SimulatedClock nodeC = new SimulatedClock("Node C", +2000);
        SimulatedClock[] drifting = {nodeA, nodeB, nodeC};
        long real = System.currentTimeMillis();
        Log.info("CLOCK", "Reference (true) time: " + Log.time(real));
        for (SimulatedClock c : drifting) {
            Log.info("CLOCK", c.getName() + " local time: " + Log.time(c.now()) + "   (offset "
                    + ClockServer.signed(c.getOffset()) + " ms)");
        }

        // ------------------------------------------------------------------ B. Cristian
        Log.section("B. CRISTIAN'S ALGORITHM (time server, 120 ms one-way network delay)");
        SimulatedClock serverClock = new SimulatedClock("TimeServer", 0);
        ClockServer timeServer = new ClockServer(serverClock, 7000, 120);
        timeServer.start();
        for (SimulatedClock c : drifting) {
            CristianClient.synchronize(c, "localhost", 7000);
            System.out.println();
        }
        for (SimulatedClock c : drifting) {
            long error = Math.abs(c.now() - serverClock.now());
            Log.info("CHECK", c.getName() + " differs from the time server by " + error + " ms");
            pass &= error <= ALLOWED_ERROR_MILLIS;
        }
        timeServer.stop();

        // ------------------------------------------------------------------ C. Berkeley
        Log.section("C. BERKELEY ALGORITHM (coordinator + 4 nodes, 40 ms one-way delay)");
        SimulatedClock coordinatorClock = new SimulatedClock("Coordinator", +1500);
        long[] offsets = {+5000, +8000, -1000, +3000};
        List<ClockServer> nodes = new ArrayList<>();
        List<BerkeleyCoordinator.Endpoint> endpoints = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            ClockServer node = new ClockServer(new SimulatedClock("Node " + (i + 1), offsets[i]), 7001 + i, 40);
            node.start();
            nodes.add(node);
            endpoints.add(new BerkeleyCoordinator.Endpoint("Node " + (i + 1), 7001 + i));
        }
        Log.info("CLOCK", "Coordinator clock offset " + ClockServer.signed(coordinatorClock.getOffset()) + " ms");
        for (ClockServer node : nodes) {
            Log.info("CLOCK", node.getClock().getName() + " clock offset "
                    + ClockServer.signed(node.getClock().getOffset()) + " ms");
        }
        System.out.println();
        long maxLeft = new BerkeleyCoordinator(coordinatorClock, endpoints).synchronize();
        pass &= maxLeft <= ALLOWED_ERROR_MILLIS;
        for (ClockServer node : nodes) {
            node.stop();
        }

        // ------------------------------------------------------------------ D. Mini Discord
        Log.section("D. MINI DISCORD USING THE SYNCHRONIZED CLOCK");
        ChatState chat = new ChatState();
        chat.registerUser("Orion");
        chat.joinChannel("Orion", "#general");
        Message stamped = chat.sendMessage("Orion", "#general", "Timestamped by Node A's synchronized clock",
                nodeA.now());
        Log.info("CHAT", "Message timestamp from synchronized clock: " + Log.time(stamped.getTimestamp())
                + "   true time: " + Log.time(System.currentTimeMillis()));

        Log.section("Result");
        Log.info("RESULT", pass ? "PASS - all clocks within " + ALLOWED_ERROR_MILLIS + " ms after synchronization"
                : "FAIL - clocks are still too far apart");
        System.exit(pass ? 0 : 1);
    }
}
