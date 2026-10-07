package exp7;

import minidiscord.common.Log;

/**
 * The load balancer as its own process (multi-terminal use). Backends are expected on 5201-5203.
 *   java exp7.LoadBalancerMain [round_robin|least_connections]
 */
public class LoadBalancerMain {
    public static void main(String[] args) throws Exception {
        LoadBalancer.Strategy strategy = args.length > 0
                ? LoadBalancer.Strategy.valueOf(args[0].toUpperCase()) : LoadBalancer.Strategy.ROUND_ROBIN;
        Log.banner("7", "LOAD BALANCER");
        LoadBalancer lb = new LoadBalancer(5200, strategy);
        for (int i = 1; i <= 3; i++) {
            lb.addBackend("Server " + i, 5200 + i);
        }
        lb.logNextRequests(Integer.MAX_VALUE);
        lb.start();
        Log.info("LOAD BALANCER", "Listening on port 5200, algorithm " + strategy + ", backends 5201-5203");
        Thread.currentThread().join();
    }
}
