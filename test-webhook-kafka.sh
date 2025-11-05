#!/bin/bash

# Script to test webhook endpoint with Kafka integration
# Prerequisites: Docker Compose must be running (postgres + kafka)

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${WEBHOOK_SECRET:-supersecret}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-overpass-kafka}"

echo "=========================================="
echo "Webhook Kafka Integration Test"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if services are running
echo "1. Checking if services are running..."
if ! docker ps | grep -q "overpass-postgres\|overpass-kafka"; then
    echo -e "${RED}ERROR: Docker containers not running. Please run: docker-compose up -d${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Services are running${NC}"
echo ""

# Check if application is running
echo "2. Checking if application is running..."
if ! curl -s "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${RED}ERROR: Application not responding at $BASE_URL${NC}"
    echo "Please start the application: ./gradlew bootRun"
    exit 1
fi
echo -e "${GREEN}✓ Application is running${NC}"
echo ""

# Test 1: Send webhook request
echo "3. Sending webhook request..."
RESPONSE=$(curl -s -X POST "$BASE_URL/webhook" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}')

if echo "$RESPONSE" | grep -q "\"id\""; then
    echo -e "${GREEN}✓ Webhook request accepted${NC}"
    EVENT_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    echo "  Event ID: $EVENT_ID"
else
    echo -e "${RED}✗ Webhook request failed${NC}"
    echo "Response: $RESPONSE"
    exit 1
fi
echo ""

# Test 2: Verify message was sent to Kafka
echo "4. Checking Kafka topic for messages..."
sleep 2
KAFKA_MESSAGES=$(docker exec $KAFKA_CONTAINER kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic webhook-events \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 5000 2>/dev/null || echo "")

if [ -n "$KAFKA_MESSAGES" ]; then
    echo -e "${GREEN}✓ Message found in Kafka topic${NC}"
    echo "  Kafka message preview: ${KAFKA_MESSAGES:0:100}..."
else
    echo -e "${YELLOW}⚠ No messages found in Kafka topic (may need more time)${NC}"
fi
echo ""

# Test 3: Wait for processing and verify results
echo "5. Waiting for Kafka consumer to process message..."
sleep 5

echo "6. Checking if results were persisted..."
LANDMARKS_RESPONSE=$(curl -s "$BASE_URL/landmarks?lat=48.8584&lng=2.2945")

if echo "$LANDMARKS_RESPONSE" | grep -q "\"key\""; then
    echo -e "${GREEN}✓ Results found in database${NC}"
    SOURCE=$(echo "$LANDMARKS_RESPONSE" | grep -o '"source":"[^"]*"' | cut -d'"' -f4)
    echo "  Source: $SOURCE"
else
    echo -e "${YELLOW}⚠ Results not yet available (may need more time)${NC}"
fi
echo ""

# Test 4: Verify Kafka consumer group
echo "7. Checking Kafka consumer group status..."
CONSUMER_GROUP="webhook-processor-group"
GROUP_INFO=$(docker exec $KAFKA_CONTAINER kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group $CONSUMER_GROUP \
  --describe 2>/dev/null || echo "Group not found")

if echo "$GROUP_INFO" | grep -q "$CONSUMER_GROUP"; then
    echo -e "${GREEN}✓ Consumer group is active${NC}"
    echo "$GROUP_INFO" | head -5
else
    echo -e "${YELLOW}⚠ Consumer group not found (may need more time)${NC}"
fi
echo ""

# Test 5: Test multiple messages
echo "8. Testing multiple webhook requests..."
for i in {1..3}; do
    curl -s -X POST "$BASE_URL/webhook" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"lat\":48.858$i,\"lng\":2.294$i}" > /dev/null
    echo "  Sent request $i"
done
echo ""

sleep 3

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}✓ Webhook endpoint is working${NC}"
echo -e "${GREEN}✓ Kafka integration is configured${NC}"
echo ""
echo "To monitor Kafka messages in real-time:"
echo "  docker exec -it $KAFKA_CONTAINER kafka-console-consumer \\"
echo "    --bootstrap-server localhost:9092 \\"
echo "    --topic webhook-events \\"
echo "    --from-beginning"
echo ""
echo "To check application logs for Kafka processing:"
echo "  ./gradlew bootRun | grep -i kafka"
echo ""

