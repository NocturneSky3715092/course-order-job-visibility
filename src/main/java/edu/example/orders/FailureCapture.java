package edu.example.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FailureCapture {
    private final String baseUrl;
    private final String key;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    public FailureCapture(@Value("${order.monitor.base-url}") String baseUrl,
                          @Value("${order.monitor.api-key}") String key, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.key = key;
        this.json = json;
    }

    public void capture(OrderJobs.Order order, RuntimeException failure) throws IOException, InterruptedException {
        if (key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY before running jobs");
        String title = order.job() + " job failed";
        String body = json.writeValueAsString(Map.of(
                "title", title, "message", failure.getMessage(), "level", "error",
                "fingerprint", new String[]{order.job().name(), order.orderId()},
                "exception", failure.toString(),
                "context", Map.of("orderId", order.orderId(), "job", order.job().name())));
        String eventKey = UUID.nameUUIDFromBytes((order.orderId() + ":" + order.job() + ":" + failure.getMessage())
                .getBytes(StandardCharsets.UTF_8)).toString();
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/errors/capture"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", eventKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = json.readTree(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                long seconds = response.headers().firstValue("Retry-After")
                        .flatMap(value -> { try { return java.util.Optional.of(Long.parseLong(value)); }
                            catch (NumberFormatException ignored) { return java.util.Optional.empty(); } })
                        .orElse(1L << attempt);
                Thread.sleep(Math.min(Math.max(seconds, 0), 30) * 1000);
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                throw new CaptureRejected(response.statusCode(), error.path("code").asText("UNKNOWN"), error.toString());
            }
            if (response.statusCode() >= 400) throw new IOException("Capture HTTP " + response.statusCode());
            return;
        }
    }

    public static class CaptureRejected extends RuntimeException {
        public final int status;
        public final String code;
        CaptureRejected(int status, String code, String detail) {
            super(detail);
            this.status = status;
            this.code = code;
        }
    }
}
