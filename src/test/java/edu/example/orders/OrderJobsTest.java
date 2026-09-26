package edu.example.orders;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrderJobsTest {
    private final OrderJobs jobs = new OrderJobs();

    @Test
    void receiptsRequireAnEmailButCheckoutOnlyRequiresAuthorizedPayment() {
        var order = new OrderJobs.Order("course-104", OrderJobs.Job.RECEIPTS, true, true, false, true);
        assertThrows(IllegalStateException.class, () -> jobs.execute(order));
        var checkout = new OrderJobs.Order("course-104", OrderJobs.Job.CHECKOUT, true, false, false, false);
        assertEquals("CHECKOUT completed for order course-104", jobs.execute(checkout));
    }
}
