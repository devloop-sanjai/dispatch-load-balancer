package com.freightfox.dispatch.service;

import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse.OrderView;
import com.freightfox.dispatch.dto.DispatchPlanResponse.UnassignedOrderView;
import com.freightfox.dispatch.dto.DispatchPlanResponse.VehiclePlan;
import com.freightfox.dispatch.dto.OrderRequest;
import com.freightfox.dispatch.dto.VehicleRequest;
import com.freightfox.dispatch.entity.DeliveryOrder;
import com.freightfox.dispatch.entity.Vehicle;
import com.freightfox.dispatch.exception.DispatchNotReadyException;
import com.freightfox.dispatch.exception.InvalidRequestException;
import com.freightfox.dispatch.optimizer.DispatchOptimizer;
import com.freightfox.dispatch.optimizer.DispatchResult;
import com.freightfox.dispatch.optimizer.OrderInput;
import com.freightfox.dispatch.optimizer.UnassignedOrder;
import com.freightfox.dispatch.optimizer.VehicleInput;
import com.freightfox.dispatch.optimizer.VehicleRoute;
import com.freightfox.dispatch.repository.DeliveryOrderRepository;
import com.freightfox.dispatch.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    private final DeliveryOrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DispatchOptimizer optimizer;

    public DispatchService(DeliveryOrderRepository orderRepository,
                           VehicleRepository vehicleRepository,
                           DispatchOptimizer optimizer) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.optimizer = optimizer;
    }

    /** Upsert: one batched SELECT for existing ids, then insert new / update existing in one flush. */
    @Transactional
    public void saveOrders(List<OrderRequest> requests) {
        List<String> ids = requests.stream().map(OrderRequest::orderId).toList();
        rejectDuplicates(ids, "orderId");

        Map<String, DeliveryOrder> existing = orderRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DeliveryOrder::getOrderId, Function.identity()));

        List<DeliveryOrder> toSave = new ArrayList<>(requests.size());
        for (OrderRequest r : requests) {
            DeliveryOrder entity = existing.computeIfAbsent(r.orderId(), DeliveryOrder::new);
            entity.update(r.latitude(), r.longitude(), r.address(), r.packageWeight(), r.priority());
            toSave.add(entity);
        }
        orderRepository.saveAll(toSave);
    }

    @Transactional
    public void saveVehicles(List<VehicleRequest> requests) {
        List<String> ids = requests.stream().map(VehicleRequest::vehicleId).toList();
        rejectDuplicates(ids, "vehicleId");

        Map<String, Vehicle> existing = vehicleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Vehicle::getVehicleId, Function.identity()));

        List<Vehicle> toSave = new ArrayList<>(requests.size());
        for (VehicleRequest r : requests) {
            Vehicle entity = existing.computeIfAbsent(r.vehicleId(), Vehicle::new);
            entity.update(r.capacity(), r.currentLatitude(), r.currentLongitude(), r.currentAddress());
            toSave.add(entity);
        }
        vehicleRepository.saveAll(toSave);
    }

    @Transactional
    public void reset() {
        orderRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
    }

    @Transactional(readOnly = true)
    public DispatchPlanResponse buildPlan() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        if (vehicles.isEmpty()) {
            throw new DispatchNotReadyException(
                    "No vehicles registered. POST /api/dispatch/vehicles before requesting a plan.");
        }
        List<DeliveryOrder> orders = orderRepository.findAll();

        Map<String, DeliveryOrder> ordersById = orders.stream()
                .collect(Collectors.toMap(DeliveryOrder::getOrderId, Function.identity()));
        Map<String, Vehicle> vehiclesById = vehicles.stream()
                .collect(Collectors.toMap(Vehicle::getVehicleId, Function.identity()));

        List<OrderInput> orderInputs = orders.stream()
                .map(o -> new OrderInput(o.getOrderId(), o.getLatitude(), o.getLongitude(),
                        o.getPackageWeight(), o.getPriority()))
                .toList();
        List<VehicleInput> vehicleInputs = vehicles.stream()
                .map(v -> new VehicleInput(v.getVehicleId(), v.getCapacity(),
                        v.getCurrentLatitude(), v.getCurrentLongitude()))
                .toList();

        DispatchResult result = optimizer.optimize(orderInputs, vehicleInputs);

        List<VehiclePlan> plans = new ArrayList<>(result.routes().size());
        for (VehicleRoute route : result.routes()) {
            List<OrderView> assigned = route.orders().stream()
                    .map(o -> toView(ordersById.get(o.orderId())))
                    .toList();
            plans.add(new VehiclePlan(
                    route.vehicleId(),
                    route.capacity(),
                    vehiclesById.get(route.vehicleId()).getCurrentAddress(),
                    route.totalLoad(),
                    formatKm(route.totalDistanceKm()),
                    assigned));
        }

        List<UnassignedOrderView> unassigned = new ArrayList<>(result.unassigned().size());
        for (UnassignedOrder u : result.unassigned()) {
            DeliveryOrder o = ordersById.get(u.order().orderId());
            unassigned.add(new UnassignedOrderView(o.getOrderId(), o.getLatitude(), o.getLongitude(),
                    o.getAddress(), o.getPackageWeight(), o.getPriority(), u.reason()));
        }
        return new DispatchPlanResponse(plans, unassigned);
    }

    private static OrderView toView(DeliveryOrder o) {
        return new OrderView(o.getOrderId(), o.getLatitude(), o.getLongitude(),
                o.getAddress(), o.getPackageWeight(), o.getPriority());
    }

    private static String formatKm(double km) {
        return String.format(Locale.ROOT, "%.2f km", km);
    }

    private static void rejectDuplicates(List<String> ids, String field) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new InvalidRequestException(
                    "Duplicate " + field + " values in request: " + duplicates.stream().sorted().toList());
        }
    }
}
