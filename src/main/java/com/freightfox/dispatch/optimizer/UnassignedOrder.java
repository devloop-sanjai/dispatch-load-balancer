package com.freightfox.dispatch.optimizer;

public record UnassignedOrder(OrderInput order, String reason) {
}
