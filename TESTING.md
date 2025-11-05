# Testing Guide - Webhook Endpoint

This guide explains how to test your webhook application locally.

## Prerequisites

- Java 22
- Docker and Docker Compose
- Gradle 8.x+

## Step 1: Start Infrastructure (PostgreSQL + Kafka)

Start PostgreSQL and Kafka using Docker Compose:

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** on port `5432`
- **Zookeeper** on port `2181`
- **Kafka** on port `9092`

Verify services are running:

```bash
docker-compose ps
```

You should see all three services with status "Up".

## Step 2: Configure Environment Variables

The application uses these defaults (can be overridden):

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/overpass
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export WEBHOOK_SECRET=supersecret
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_WEBHOOK_TOPIC=webhook-events
export KAFKA_CONSUMER_GROUP=webhook-processor-group
```

Or create a `.env` file:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/overpass
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
WEBHOOK_SECRET=supersecret
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## Step 3: Build and Run the Application

```bash
# Build the application
./gradlew build

# Run the application
./gradlew bootRun
```

Or with local profile for debug logging:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The application will:
- Run Flyway migrations (creates database schema)
- Connect to Kafka at `localhost:9092`
- Start listening on port `8080`
- Auto-create Kafka topic `webhook-events` if it doesn't exist

## Step 4: Test the Webhook Endpoint

### Health Check (Verify Application is Running)

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### Test Webhook with Valid Coordinates

Send a webhook request to process coordinates asynchronously:

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

**Expected Response** (202 Accepted):
```json
{
  "id": "uuid-here"
}
```

**What happens:**
1. Webhook request is received and validated
2. Event is sent to Kafka topic `webhook-events`
3. Response is returned immediately (202 Accepted)
4. Kafka consumer processes the event asynchronously:
   - Queries Overpass API for nearby landmarks
   - Stores results in database
   - Populates cache

### Monitor Application Logs

Watch the application logs to see Kafka processing:

```bash
# In another terminal, watch logs
docker logs -f overpass-kafka  # Kafka logs
```

Application logs will show:
```
INFO  - Received webhook request: lat=48.8584, lng=2.2945
INFO  - Sending webhook event to Kafka topic webhook-events: eventId=...
INFO  - Successfully sent webhook event to Kafka: eventId=..., offset=...
INFO  - Received webhook event from Kafka: eventId=..., lat=48.8584, lng=2.2945, partition=0, offset=0
INFO  - Successfully processed webhook event: eventId=...
```

### Verify Results via Landmarks Endpoint

After processing (wait a few seconds), check the results:

```bash
curl "http://localhost:8080/landmarks?lat=48.8584&lng=2.2945"
```

**Expected Response** (200 OK):
```json
{
  "key": {
    "lat": 48.8584,
    "lng": 2.2945,
    "radiusMeters": 500
  },
  "source": "db",
  "landmarks": [
    {
      "id": "uuid",
      "name": "Eiffel Tower",
      "osmType": "way",
      "osmId": 123456,
      "lat": 48.8584,
      "lng": 2.2945,
      "tags": {...}
    }
  ]
}
```

The `source` field indicates:
- `"cache"` - Retrieved from cache
- `"db"` - Retrieved from database (cache miss)
- `"none"` - No data found

## Step 5: Test Different Scenarios

### Test Idempotency (Duplicate Request)

Send the same coordinates again:

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

This should return immediately with the same event ID (idempotent behavior).

### Test Invalid Authentication

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer wrongsecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

**Expected Response** (401 Unauthorized):
```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid or missing authentication token"
}
```

### Test Missing Authentication

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

**Expected Response** (401 Unauthorized)

### Test Invalid Coordinates

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":123,"lng":200}'
```

**Expected Response** (400 Bad Request):
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid coordinates"
}
```

### Test Different Locations

```bash
# Paris - Notre-Dame
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8530,"lng":2.3499}'

# New York - Times Square
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":40.7580,"lng":-73.9855}'

# London - Big Ben
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":51.4994,"lng":-0.1245}'
```

## Step 6: Monitor Kafka Topics

### List Kafka Topics

```bash
docker exec -it overpass-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

You should see `webhook-events` topic.

### Check Topic Details

```bash
docker exec -it overpass-kafka kafka-topics --bootstrap-server localhost:9092 \
  --describe --topic webhook-events
```

### Consume Messages from Topic (for debugging)

```bash
docker exec -it overpass-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic webhook-events \
  --from-beginning \
  --property print.key=true \
  --property print.value=true
```

This will show messages as they're processed (JSON format).

## Step 7: Verify Database

### Connect to PostgreSQL

```bash
docker exec -it overpass-postgres psql -U postgres -d overpass
```

### Check Coordinate Requests

```sql
SELECT id, key_lat, key_lng, radius_m, status, requested_at 
FROM coordinate_request 
ORDER BY requested_at DESC 
LIMIT 10;
```

### Check Landmarks

```sql
SELECT l.id, l.name, l.osm_type, l.osm_id, l.lat, l.lng, cr.key_lat, cr.key_lng
FROM landmark l
JOIN coordinate_request cr ON l.coord_request_id = cr.id
ORDER BY l.created_at DESC
LIMIT 20;
```

## Troubleshooting

### Kafka Connection Issues

If Kafka isn't connecting:

1. **Check Kafka is running:**
   ```bash
   docker-compose ps
   docker logs overpass-kafka
   ```

2. **Verify Kafka is accessible:**
   ```bash
   docker exec -it overpass-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
   ```

3. **Check application logs** for Kafka connection errors

### Consumer Not Processing Messages

1. **Check consumer logs** in application output
2. **Verify consumer group:**
   ```bash
   docker exec -it overpass-kafka kafka-consumer-groups \
     --bootstrap-server localhost:9092 \
     --group webhook-processor-group \
     --describe
   ```

3. **Check if messages are in topic:**
   ```bash
   docker exec -it overpass-kafka kafka-run-class kafka.tools.GetOffsetShell \
     --broker-list localhost:9092 \
     --topic webhook-events
   ```

### Database Connection Issues

1. **Check PostgreSQL is running:**
   ```bash
   docker-compose ps
   docker logs overpass-postgres
   ```

2. **Verify connection:**
   ```bash
   docker exec -it overpass-postgres psql -U postgres -d overpass -c "SELECT 1;"
   ```

### Application Not Starting

1. **Check Java version:**
   ```bash
   java -version  # Should be Java 22
   ```

2. **Verify port 8080 is available:**
   ```bash
   lsof -i :8080
   ```

3. **Check application logs** for specific errors

## Quick Test Script

Save this as `test-webhook.sh`:

```bash
#!/bin/bash

# Configuration
BASE_URL="http://localhost:8080"
TOKEN="supersecret"

echo "1. Health Check..."
curl -s "$BASE_URL/actuator/health" | jq .

echo -e "\n2. Sending webhook request..."
RESPONSE=$(curl -s -X POST "$BASE_URL/webhook" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}')

echo "$RESPONSE" | jq .
EVENT_ID=$(echo "$RESPONSE" | jq -r '.id')

echo -e "\n3. Waiting 5 seconds for processing..."
sleep 5

echo -e "\n4. Checking results..."
curl -s "$BASE_URL/landmarks?lat=48.8584&lng=2.2945" | jq .

echo -e "\n✅ Test complete!"
```

Make it executable and run:

```bash
chmod +x test-webhook.sh
./test-webhook.sh
```

## Next Steps

- Check application logs to monitor processing
- Verify landmarks are stored in database
- Test cache behavior by querying same coordinates multiple times
- Test error scenarios (invalid coordinates, network issues, etc.)

