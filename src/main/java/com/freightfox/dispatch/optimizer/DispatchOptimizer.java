package com.freightfox.dispatch.optimizer;

import java.util.List;

/**
 * Strategy interface: different allocation algorithms can be swapped in
 * without touching controllers or services.
 */
public interface DispatchOptimizer {

    DispatchResult optimize(List<OrderInput> orders, List<VehicleInput> vehicles);
}
