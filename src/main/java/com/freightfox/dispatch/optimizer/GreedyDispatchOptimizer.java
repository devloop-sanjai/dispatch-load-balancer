package com.freightfox.dispatch.optimizer;

import com.freightfox.dispatch.domain.Priority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Priority-first, nearest-vehicle greedy allocation.
 *
 * <p>Phase 1 (assignment): orders are processed HIGH -> MEDIUM -> LOW (heavier first inside a tier, so
 * big parcels are placed before capacity fragments). Each order goes to the vehicle that has enough
 * remaining capacity and whose current route tail is closest (smallest added distance).
 * Cost: O(orders x vehicles).
 *
 * <p>Phase 2 (sequencing): per vehicle, stops are visited priority tier by tier (HIGH first), and inside a
 * tier by nearest-neighbour from the previous stop. Distance is then measured along that sequence.
 *
 * <p>This is a heuristic (the underlying problem is NP-hard); it is deterministic and scales to large inputs.
 */
public final class GreedyDispatchOptimizer implements DispatchOptimizer {

    private static final double EPS = 1e-9;

    private final DistanceCalculator distance;

    public GreedyDispatchOptimizer(DistanceCalculator distance) {
        this.distance = Objects.requireNonNull(distance, "distance");
    }

    @Override
    public DispatchResult optimize(List<OrderInput> orders, List<VehicleInput> vehicles) {
        List<VehicleInput> fleet = new ArrayList<>(vehicles);
        fleet.sort(Comparator.comparing(VehicleInput::vehicleId));
        int m = fleet.size();

        double[] remaining = new double[m];
        double[] tailLat = new double[m];
        double[] tailLon = new double[m];
        List<List<OrderInput>> assigned = new ArrayList<>(m);
        double maxCapacity = 0;
        for (int i = 0; i < m; i++) {
            VehicleInput v = fleet.get(i);
            remaining[i] = v.capacity();
            tailLat[i] = v.latitude();
            tailLon[i] = v.longitude();
            assigned.add(new ArrayList<>());
            maxCapacity = Math.max(maxCapacity, v.capacity());
        }

        List<OrderInput> queue = new ArrayList<>(orders);
        queue.sort(Comparator.comparing(OrderInput::priority)
                .thenComparing(Comparator.<OrderInput>comparingDouble(OrderInput::weight).reversed())
                .thenComparing(OrderInput::orderId));

        List<UnassignedOrder> unassigned = new ArrayList<>();
        for (OrderInput order : queue) {
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < m; i++) {
                if (remaining[i] + EPS < order.weight()) {
                    continue;
                }
                double d = distance.distanceKm(tailLat[i], tailLon[i], order.latitude(), order.longitude());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = i;
                }
            }
            if (best < 0) {
                unassigned.add(new UnassignedOrder(order, reasonFor(order, m, maxCapacity)));
                continue;
            }
            assigned.get(best).add(order);
            remaining[best] -= order.weight();
            tailLat[best] = order.latitude();
            tailLon[best] = order.longitude();
        }

        List<VehicleRoute> routes = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            routes.add(buildRoute(fleet.get(i), assigned.get(i)));
        }
        return new DispatchResult(routes, unassigned);
    }

    private VehicleRoute buildRoute(VehicleInput vehicle, List<OrderInput> assigned) {
        Map<Priority, List<OrderInput>> tiers = new EnumMap<>(Priority.class);
        for (OrderInput o : assigned) {
            tiers.computeIfAbsent(o.priority(), p -> new ArrayList<>()).add(o);
        }

        List<OrderInput> sequence = new ArrayList<>(assigned.size());
        double lat = vehicle.latitude();
        double lon = vehicle.longitude();
        double totalDistance = 0;
        double totalLoad = 0;

        for (List<OrderInput> tier : tiers.values()) { // EnumMap iterates HIGH, MEDIUM, LOW
            List<OrderInput> pending = new ArrayList<>(tier);
            while (!pending.isEmpty()) {
                int nearest = 0;
                double nearestDistance = Double.MAX_VALUE;
                for (int j = 0; j < pending.size(); j++) {
                    OrderInput candidate = pending.get(j);
                    double d = distance.distanceKm(lat, lon, candidate.latitude(), candidate.longitude());
                    if (d < nearestDistance) {
                        nearestDistance = d;
                        nearest = j;
                    }
                }
                OrderInput next = pending.get(nearest);
                int last = pending.size() - 1;
                pending.set(nearest, pending.get(last)); // O(1) removal
                pending.remove(last);

                sequence.add(next);
                totalDistance += nearestDistance;
                totalLoad += next.weight();
                lat = next.latitude();
                lon = next.longitude();
            }
        }
        return new VehicleRoute(vehicle.vehicleId(), vehicle.capacity(), List.copyOf(sequence),
                totalLoad, totalDistance);
    }

    private String reasonFor(OrderInput order, int vehicleCount, double maxCapacity) {
        if (vehicleCount == 0) {
            return "No vehicles available";
        }
        if (order.weight() > maxCapacity + EPS) {
            return String.format(Locale.ROOT,
                    "Package weight %.2f exceeds the largest vehicle capacity (%.2f)", order.weight(), maxCapacity);
        }
        return "No vehicle has enough remaining capacity";
    }
}
