package com.freightfox.dispatch.optimizer;

import com.freightfox.dispatch.domain.Priority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GreedyDispatchOptimizerTest {

    private final GreedyDispatchOptimizer optimizer = new GreedyDispatchOptimizer(new HaversineDistanceCalculator());

    private static OrderInput order(String id, double lat, double lon, double weight, Priority p) {
        return new OrderInput(id, lat, lon, weight, p);
    }

    private static VehicleInput vehicle(String id, double capacity, double lat, double lon) {
        return new VehicleInput(id, capacity, lat, lon);
    }

    @Test
    void emptyOrdersGiveEmptyRoutesForEveryVehicle() {
        DispatchResult result = optimizer.optimize(List.of(), List.of(vehicle("V1", 10, 0, 0), vehicle("V2", 10, 1, 1)));

        assertThat(result.routes()).hasSize(2);
        assertThat(result.routes()).allSatisfy(r -> {
            assertThat(r.orders()).isEmpty();
            assertThat(r.totalLoad()).isZero();
            assertThat(r.totalDistanceKm()).isZero();
        });
        assertThat(result.unassigned()).isEmpty();
    }

    @Test
    void noVehiclesMeansEverythingIsUnassigned() {
        DispatchResult result = optimizer.optimize(List.of(order("O1", 0, 0, 5, Priority.HIGH)), List.of());

        assertThat(result.routes()).isEmpty();
        assertThat(result.unassigned()).hasSize(1);
        assertThat(result.unassigned().get(0).reason()).contains("No vehicles");
    }

    @Test
    void orderGoesToNearestVehicleWithCapacity() {
        DispatchResult result = optimizer.optimize(
                List.of(order("O1", 0.0, 0.1, 5, Priority.HIGH)),
                List.of(vehicle("FAR", 100, 10, 10), vehicle("NEAR", 100, 0, 0)));

        assertThat(routeOf(result, "NEAR").orders()).extracting(OrderInput::orderId).containsExactly("O1");
        assertThat(routeOf(result, "FAR").orders()).isEmpty();
    }

    @Test
    void nearestVehicleIsSkippedWhenItLacksCapacity() {
        DispatchResult result = optimizer.optimize(
                List.of(order("O1", 0.0, 0.1, 50, Priority.HIGH)),
                List.of(vehicle("NEAR_SMALL", 10, 0, 0), vehicle("FAR_BIG", 100, 10, 10)));

        assertThat(routeOf(result, "FAR_BIG").orders()).hasSize(1);
        assertThat(routeOf(result, "NEAR_SMALL").orders()).isEmpty();
    }

    @Test
    void highPriorityWinsWhenCapacityIsScarce() {
        DispatchResult result = optimizer.optimize(
                List.of(order("LOW1", 0, 0, 10, Priority.LOW),
                        order("MED1", 0, 0, 10, Priority.MEDIUM),
                        order("HIGH1", 0, 0, 10, Priority.HIGH)),
                List.of(vehicle("V1", 10, 0, 0)));

        assertThat(routeOf(result, "V1").orders()).extracting(OrderInput::orderId).containsExactly("HIGH1");
        assertThat(result.unassigned()).extracting(u -> u.order().orderId()).containsExactlyInAnyOrder("LOW1", "MED1");
    }

    @Test
    void orderHeavierThanEveryVehicleIsUnassignedWithReason() {
        DispatchResult result = optimizer.optimize(
                List.of(order("BIG", 0, 0, 500, Priority.HIGH)),
                List.of(vehicle("V1", 100, 0, 0), vehicle("V2", 150, 0, 0)));

        assertThat(result.unassigned()).hasSize(1);
        assertThat(result.unassigned().get(0).reason()).contains("exceeds the largest vehicle capacity");
        assertThat(result.routes()).allSatisfy(r -> assertThat(r.orders()).isEmpty());
    }

    @Test
    void fullFleetLeavesRemainingOrdersUnassigned() {
        DispatchResult result = optimizer.optimize(
                List.of(order("A", 0, 0, 60, Priority.HIGH), order("B", 0, 0, 60, Priority.MEDIUM)),
                List.of(vehicle("V1", 100, 0, 0)));

        assertThat(result.unassigned()).hasSize(1);
        assertThat(result.unassigned().get(0).reason()).contains("remaining capacity");
    }

    @Test
    void orderExactlyFillingCapacityIsAccepted() {
        DispatchResult result = optimizer.optimize(
                List.of(order("A", 0, 0, 100, Priority.LOW)), List.of(vehicle("V1", 100, 0, 0)));

        assertThat(result.unassigned()).isEmpty();
        assertThat(routeOf(result, "V1").totalLoad()).isEqualTo(100.0);
    }

    @Test
    void highPriorityStopsAreVisitedBeforeLowEvenWhenFarther() {
        DispatchResult result = optimizer.optimize(
                List.of(order("LOW_NEAR", 0, 0.01, 1, Priority.LOW),
                        order("HIGH_FAR", 0, 1.0, 1, Priority.HIGH)),
                List.of(vehicle("V1", 100, 0, 0)));

        assertThat(routeOf(result, "V1").orders()).extracting(OrderInput::orderId)
                .containsExactly("HIGH_FAR", "LOW_NEAR");
    }

    @Test
    void stopsInTheSameTierFollowNearestNeighbourOrder() {
        DispatchResult result = optimizer.optimize(
                List.of(order("C", 0, 3, 1, Priority.HIGH),
                        order("A", 0, 1, 1, Priority.HIGH),
                        order("B", 0, 2, 1, Priority.HIGH)),
                List.of(vehicle("V1", 100, 0, 0)));

        assertThat(routeOf(result, "V1").orders()).extracting(OrderInput::orderId).containsExactly("A", "B", "C");
    }

    @Test
    void totalDistanceIsMeasuredAlongTheRouteFromTheVehiclesStart() {
        DispatchResult result = optimizer.optimize(
                List.of(order("A", 1, 0, 1, Priority.HIGH), order("B", 2, 0, 1, Priority.HIGH)),
                List.of(vehicle("V1", 100, 0, 0)));

        // 0 -> 1 -> 2 degrees of latitude = 2 x 111.195 km
        assertThat(routeOf(result, "V1").totalDistanceKm()).isCloseTo(2 * 111.195, within(0.05));
    }

    @Test
    void resultIsDeterministic() {
        List<OrderInput> orders = randomOrders(new Random(7), 100);
        List<VehicleInput> fleet = randomFleet(new Random(8), 4);

        assertThat(optimizer.optimize(orders, fleet)).isEqualTo(optimizer.optimize(orders, fleet));
    }

    @Test
    void invariantsHoldOnRandomisedLargeInput() {
        Random random = new Random(42);
        List<OrderInput> orders = randomOrders(random, 2000);
        List<VehicleInput> fleet = randomFleet(random, 10);

        DispatchResult result = optimizer.optimize(orders, fleet);

        Set<String> seen = new HashSet<>();
        for (VehicleRoute route : result.routes()) {
            double load = 0;
            for (OrderInput o : route.orders()) {
                assertThat(seen.add(o.orderId())).as("order %s assigned once", o.orderId()).isTrue();
                load += o.weight();
            }
            assertThat(load).isLessThanOrEqualTo(route.capacity() + 1e-6);
            assertThat(route.totalLoad()).isCloseTo(load, within(1e-6));
        }
        result.unassigned().forEach(u -> assertThat(seen.add(u.order().orderId())).isTrue());
        assertThat(seen).hasSize(orders.size());
    }

    private static VehicleRoute routeOf(DispatchResult result, String vehicleId) {
        return result.routes().stream().filter(r -> r.vehicleId().equals(vehicleId)).findFirst().orElseThrow();
    }

    private static List<OrderInput> randomOrders(Random random, int n) {
        Priority[] priorities = Priority.values();
        List<OrderInput> orders = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            orders.add(order("O" + i, 28.4 + random.nextDouble() * 0.4, 77.0 + random.nextDouble() * 0.5,
                    1 + random.nextInt(40), priorities[random.nextInt(priorities.length)]));
        }
        return orders;
    }

    private static List<VehicleInput> randomFleet(Random random, int n) {
        List<VehicleInput> fleet = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fleet.add(vehicle("V" + i, 100 + random.nextInt(100), 28.4 + random.nextDouble() * 0.4,
                    77.0 + random.nextDouble() * 0.5));
        }
        return fleet;
    }
}
