package edu.example.orders;

import org.springframework.stereotype.Service;

@Service
public class OrderJobs {
    public record Order(String orderId, Job job, boolean paymentAuthorized,
                        boolean inventoryReserved, boolean emailPresent, boolean contactPresent) {}
    public enum Job { CHECKOUT, FULFILLMENT, RECEIPTS, CUSTOMER_UPDATES }

    public String execute(Order order) {
        if (order.orderId() == null || order.orderId().isBlank() || order.job() == null)
            throw new IllegalArgumentException("orderId and job are required");
        boolean ready = switch (order.job()) {
            case CHECKOUT -> order.paymentAuthorized();
            case FULFILLMENT -> order.inventoryReserved();
            case RECEIPTS -> order.emailPresent();
            case CUSTOMER_UPDATES -> order.contactPresent();
        };
        if (!ready) throw new IllegalStateException(order.job() + " prerequisite missing for order " + order.orderId());
        return order.job() + " completed for order " + order.orderId();
    }
}
