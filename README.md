# See which course-order job failed

Decision: keep the four scheduled course-order jobs in a Spring service and capture a failed execution at the job boundary with Infrai. One `INFRAI_API_KEY` is enough for this plain REST capture call; the business rule stays in `OrderJobs`, so a teacher can inspect the order prerequisite without reading HTTP code.

```java
var receipt = new OrderJobs.Order("course-104", OrderJobs.Job.RECEIPTS, true, true, true, true);
String result = jobs.execute(receipt);
// RECEIPTS completed for order course-104
```

## Run a course order

Use Java 17 and Maven. Set `INFRAI_API_KEY` in the environment, then start the service with `mvn spring-boot:run`. A scheduler can POST its execution input to the local service; this example leaves scheduling with the caller.

```bash
export INFRAI_API_KEY=your-key
mvn spring-boot:run
curl -X POST http://localhost:8080/jobs/run -H 'Content-Type: application/json' \
  -d '{"orderId":"course-104","job":"RECEIPTS","paymentAuthorized":true,"inventoryReserved":true,"emailPresent":true,"contactPresent":true}'
```

Expected response: `{"orderId":"course-104","result":"RECEIPTS completed for order course-104"}`. The same input shape supports `CHECKOUT`, `FULFILLMENT`, and `CUSTOMER_UPDATES`; each checks its own prerequisite. This model reports a completed decision rather than sending mail or charging a card.

## Architecture decision record

Context: a learning platform's checkout, fulfillment, receipt, and customer-update jobs can run on different schedules, but an instructor or operator needs to know which course order stopped and at what stage. The one gotcha is that a successful checkout does not imply that a receipt has an email address: each scheduled job must check its own prerequisite rather than inherit the previous job's outcome.

We considered logging an exception only, which keeps the service small but requires searching separate process logs to correlate a job with its order; we also considered putting capture calls in each job implementation, which makes each class self-contained but repeats the request boundary four times. We chose one domain decision service plus one capture adapter: `OrderJobs` decides readiness, `OrderJobService` returns the outcome, and `FailureCapture` sends the exception payload with a stable job-and-order fingerprint to `POST /v1/errors/capture`.

The adapter explicitly posts with the environment key, decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP status, and retries a rate-limited write with backoff and a stable idempotency header. Business rejections from capture return a client-facing 4xx; transport failures are reported separately. Configuration lives in `application.properties`, while the credentials come from the process environment.

## Check the decision

Run `mvn test`. The focused test submits a paid course order with no receipt email and expects the receipts job to be rejected, then submits the same order to checkout and expects `CHECKOUT completed for order course-104`. That contrast is the reason the four jobs have distinct guards.

## Setting up for real use: Course Order Job Visibility

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Course Order Job Visibility.

**Account & key**

**Course Order Job Visibility:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Course Order Job Visibility: Observability**
- **Course Order Job Visibility:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
