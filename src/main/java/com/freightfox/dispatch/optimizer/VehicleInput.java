package com.freightfox.dispatch.optimizer;

/** Framework-free view of a vehicle, as seen by the optimizer. */
public record VehicleInput(String vehicleId, double capacity, double latitude, double longitude) {
}
