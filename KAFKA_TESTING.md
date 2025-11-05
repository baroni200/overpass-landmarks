# Testing Webhook with Kafka

This guide explains how to test that your webhook is properly using Kafka for asynchronous processing.

## Quick Test (Automated)

### Option 1: Run Kafka Integration Test (Recommended)

This test uses Testcontainers to spin up real Kafka and PostgreSQL instances and verifies Kafka integration:

```bash
./gradlew test --tests WebhookKafkaIntegrationTest
```

**What it tests:**
- ✅ Webhook sends messages to Kafka topic
- ✅ Kafka consumer processes messages asynchronously
- ✅ Multiple messages are processed correctly
- ✅ Database persistence after Kafka processing

### Option 2: Run All Tests

```bash
./gradlew test
```

**Note:** Regular tests use mocked Kafka (synchronous processing). Only `WebhookKafkaIntegrationTest` uses real Kafka.

## Manual Test (End-to-End)

### Step 1: Start Infrastructure

```bash
docker-compose up -d
```

Verify services:
```bash
docker-compose ps
```

### Step 2: Start Application

```bash
./gradlew bootRun
```

Keep this running in one terminal.

### Step 3: Run Test Script

In another terminal:

```bash
./test-webhook-kafka.sh
```

This script will:
1. ✅ Check services are running
2. ✅ Send webhook request
3. ✅ Verify message in Kafka topic
4. ✅ Check if results were persisted
5. ✅ Verify Kafka consumer group

### Step 4: Manual Verification

**Test webhook:**
```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

**Expected:** `202 Accepted` with event ID

**Monitor Kafka messages:**
```bash
docker exec -it overpass-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic webhook-events \
  --from-beginning
```

**Check Kafka consumer group:**
```bash
docker exec -it overpass-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group webhook-processor-group \
  --describe
```

**Verify results:**
```bash
curl "http://localhost:8080/landmarks?lat=48.8584&lng=2.2945"
```

## What to Look For

### ✅ Success Indicators

1. **Webhook Response**: Returns `202 Accepted` immediately (not waiting for processing)
2. **Kafka Topic**: Messages appear in `webhook-events` topic
3. **Application Logs**: Show:
   ```
   INFO - Sending webhook event to Kafka topic webhook-events: eventId=...
   INFO - Successfully sent webhook event to Kafka: eventId=..., offset=...
   INFO - Received webhook event from Kafka: eventId=..., lat=..., lng=...
   INFO - Successfully processed webhook event: eventId=...
   ```
4. **Consumer Group**: Active and processing messages
5. **Database**: Results appear after Kafka processing completes

### ❌ Failure Indicators

1. **No Kafka messages**: Check `spring.kafka.bootstrap-servers` configuration
2. **Consumer not processing**: Check `WebhookConsumer` is enabled (not in test profile)
3. **Immediate processing**: If webhook waits for processing, Kafka is not being used

## Troubleshooting

### Kafka Not Connecting

1. Check Kafka is running: `docker ps | grep kafka`
2. Verify bootstrap servers: `echo $KAFKA_BOOTSTRAP_SERVERS` (should be `localhost:9092`)
3. Check application logs for connection errors

### Consumer Not Processing

1. Verify `KafkaConfig` is enabled (not excluded by test profile)
2. Check `WebhookConsumer` has `@Profile("!test")`
3. Look for consumer startup logs in application output

### Messages Not Appearing in Topic

1. Wait a few seconds (async processing)
2. Check producer logs for send errors
3. Verify topic exists: `docker exec -it overpass-kafka kafka-topics --list --bootstrap-server localhost:9092`

## Test Files

- **`WebhookKafkaIntegrationTest.java`**: Automated test with Testcontainers
- **`test-webhook-kafka.sh`**: Manual test script
- **`WebhookIntegrationTest.java`**: Regular tests (mocked Kafka)

