# Payment & Verification Gateway (Kotlin + Spring Boot)

Backend service that creates payments safely (idempotency), verifies the customer with a KYC vendor,
creates orders at a payment provider (Razorpay sandbox), receives signed provider webhooks, and
processes them asynchronously through RabbitMQ with retries and a dead-letter queue.
A scheduled job reconciles payments whose webhook never arrived.

## Flow
```
Client --POST /api/v1/payments (Idempotency-Key)--> PaymentService
          |-- KycClient (mock verification vendor)  -> REJECTED_KYC stops here
          |-- PaymentProvider (Razorpay sandbox)    -> order id, status PENDING
Razorpay --POST /api/v1/webhooks/razorpay (HMAC signed)--> verify -> RabbitMQ (payments.events)
PaymentEventConsumer: idempotent (processed_events) -> applyStatus -> CAPTURED / FAILED
   on failure: 3 attempts with backoff -> dead-letter queue (payments.events.dlq)
ReconciliationJob (every 60s): PENDING older than 10 min -> ask provider -> update
```

## Stack
Kotlin 1.9, Spring Boot 3.3, Spring Data JPA, PostgreSQL + Flyway, RabbitMQ (Spring AMQP),
JUnit 5 + H2, Docker, GitHub Actions.

## Run locally
```bash
docker compose up --build          # Postgres + RabbitMQ + app on :8080
```
Without Razorpay keys the provider creates simulated orders, so everything runs locally.
For the real sandbox set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` (test mode) and `RAZORPAY_WEBHOOK_SECRET`.

## Try it
```bash
curl -X POST localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-1","amountPaise":50000,"currency":"INR"}'
# repeat the same call -> same payment id, no second provider call
# customerId "blocked-1" -> status REJECTED_KYC

# simulate a webhook (secret defaults to whsec_local in docker-compose)
BODY='{"event":"payment.captured","payload":{"payment":{"entity":{"order_id":"<providerOrderId>"}}}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac 'whsec_local' | awk '{print $2}')
curl -X POST localhost:8080/api/v1/webhooks/razorpay \
  -H 'Content-Type: application/json' -H "X-Razorpay-Signature: $SIG" -d "$BODY"
curl localhost:8080/api/v1/payments/<id>       # status CAPTURED
```
A webhook for an unknown order id fails 3 times and lands in `payments.events.dlq`
(see it at http://localhost:15672).

## Tests
```bash
gradle test      # or ./gradlew test after running `gradle wrapper` once
```

## Deploy (Render + CloudAMQP, both have free tiers)
1. Push this repo to GitHub.
2. Render: New > PostgreSQL. Copy the *Internal* host, user, password and build
   `DB_URL=jdbc:postgresql://<host>:5432/<db>` (JDBC format, not postgres://).
3. CloudAMQP: create a free "Little Lemur" instance and copy its AMQP URL into `RABBITMQ_URL`.
4. Render: New > Web Service > your repo > Runtime Docker. Set env vars: `DB_URL`, `DB_USER`,
   `DB_PASSWORD`, `RABBITMQ_URL`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`.
5. Health check path: `/actuator/health`. In the Razorpay dashboard (test mode) add the webhook URL
   `https://<your-service>.onrender.com/api/v1/webhooks/razorpay` with the same webhook secret.
