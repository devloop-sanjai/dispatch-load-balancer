package com.freightfox.dispatch.optimizer;

import java.util.List;

/**
 * Orders assigned to one vehicle, in visiting sequence.
 * Distance covers: current location -> first stop -> ... -> last stop.
 */
public record VehicleRoute(String vehicleId, double capacity, List<OrderInput> orders,
                           double totalLoad, double totalDistanceKm) {
}
