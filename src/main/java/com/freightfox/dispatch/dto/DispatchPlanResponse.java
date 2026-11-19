package com.freightfox.dispatch.dto;

import com.freightfox.dispatch.domain.Priority;

import java.util.List;

public record DispatchPlanResponse(List<VehiclePlan> dispatchPlan, List<UnassignedOrderView> unassignedOrders) {

    public record VehiclePlan(String vehicleId,
                              double capacity,
                              String currentAddress,
                              double totalLoad,
                              String totalDistance,
                              List<OrderView> assignedOrders) {
    }

    public record OrderView(String orderId,
                            double latitude,
                            double longitude,
                            String address,
                            double packageWeight,
                            Priority priority) {
    }

    public record UnassignedOrderView(String orderId,
                                      double latitude,
                                      double longitude,
                                      String address,
                                      double packageWeight,
                                      Priority priority,
                                      String reason) {
    }
}
