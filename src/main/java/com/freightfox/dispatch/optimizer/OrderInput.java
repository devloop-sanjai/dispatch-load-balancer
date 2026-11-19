package com.freightfox.dispatch.optimizer;

import com.freightfox.dispatch.domain.Priority;

/** Framework-free view of an order, as seen by the optimizer. */
public record OrderInput(String orderId, double latitude, double longitude, double weight, Priority priority) {
}
