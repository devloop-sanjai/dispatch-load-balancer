package com.freightfox.dispatch.controller;

import com.freightfox.dispatch.dto.ApiResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.OrdersRequest;
import com.freightfox.dispatch.dto.VehiclesRequest;
import com.freightfox.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispatch")
@Tag(name = "Dispatch", description = "Submit orders and vehicles, then fetch the optimized dispatch plan")
public class DispatchController {

    private final DispatchService service;

    public DispatchController(DispatchService service) {
        this.service = service;
    }

    @Operation(summary = "Submit delivery orders",
            description = "Upsert by orderId: existing orders are overwritten, new ones are created.")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse> submitOrders(@Valid @RequestBody OrdersRequest request) {
        service.saveOrders(request.orders());
        return ResponseEntity.ok(ApiResponse.success("Delivery orders accepted."));
    }

    @Operation(summary = "Submit fleet details",
            description = "Upsert by vehicleId: existing vehicles are overwritten, new ones are created.")
    @PostMapping("/vehicles")
    public ResponseEntity<ApiResponse> submitVehicles(@Valid @RequestBody VehiclesRequest request) {
        service.saveVehicles(request.vehicles());
        return ResponseEntity.ok(ApiResponse.success("Vehicle details accepted."));
    }

    @Operation(summary = "Get the optimized dispatch plan",
            description = "HIGH priority orders are assigned first; vehicle capacity is never exceeded; "
                    + "orders that cannot be placed are listed under unassignedOrders with a reason.")
    @GetMapping("/plan")
    public ResponseEntity<DispatchPlanResponse> getPlan() {
        return ResponseEntity.ok(service.buildPlan());
    }

    @Operation(summary = "Clear all orders and vehicles", description = "Convenience endpoint for repeatable testing.")
    @DeleteMapping("/reset")
    public ResponseEntity<ApiResponse> reset() {
        service.reset();
        return ResponseEntity.ok(ApiResponse.success("All dispatch data cleared."));
    }
}
