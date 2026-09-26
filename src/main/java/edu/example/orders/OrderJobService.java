package edu.example.orders;

import java.io.IOException;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class OrderJobService {
    private final OrderJobs jobs;
    private final FailureCapture failures;

    public OrderJobService(OrderJobs jobs, FailureCapture failures) {
        this.jobs = jobs;
        this.failures = failures;
    }

    public static void main(String[] args) { SpringApplication.run(OrderJobService.class, args); }

    @PostMapping("/jobs/run")
    public ResponseEntity<Map<String, String>> run(@RequestBody OrderJobs.Order order)
            throws IOException, InterruptedException {
        try {
            return ResponseEntity.ok(Map.of("orderId", order.orderId(), "result", jobs.execute(order)));
        } catch (IllegalStateException failure) {
            failures.capture(order, failure);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("orderId", order.orderId(), "result", "job failed and was captured"));
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(FailureCapture.CaptureRejected.class)
    public ResponseEntity<Map<String, String>> rejected(FailureCapture.CaptureRejected exception) {
        int status = exception.status >= 400 && exception.status < 500 ? exception.status : 502;
        return ResponseEntity.status(status).body(Map.of("error", exception.code));
    }

    @ExceptionHandler({IOException.class, InterruptedException.class})
    public ResponseEntity<Map<String, String>> transport(Exception exception) {
        if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Capture transport failed"));
    }
}
