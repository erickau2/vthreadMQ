#!/bin/bash

# VthreadMQ Consumer Examples
# Make sure VthreadMQ is running on localhost:8080

BASE_URL="http://localhost:8080/api"

echo "=== VthreadMQ Consumer Examples ==="

# 1. Basic message consumption
echo "1. Consuming messages from 'notifications' topic..."
curl "$BASE_URL/consume?topic=notifications&maxMessages=5" | jq .

echo -e "\n"

# 2. Consumer group consumption
echo "2. Consuming with consumer group 'my-service'..."
curl -X POST "$BASE_URL/consume" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "notifications",
    "consumerGroup": "my-service",
    "maxMessages": 3,
    "autoCommit": true
  }' | jq .

echo -e "\n"

# 3. Consume from specific offset
echo "3. Consuming from offset 10..."
curl "$BASE_URL/consume?topic=batch&consumerGroup=batch-processor&fromOffset=10&maxMessages=5" | jq .

echo -e "\n"

# 4. Check committed offset
echo "4. Checking committed offset for consumer group..."
curl "$BASE_URL/offset?consumerGroup=my-service&topic=notifications" | jq .

echo -e "\n"

# 5. Manual offset commit
echo "5. Manually committing offset..."
curl -X POST "$BASE_URL/commit?consumerGroup=my-service&topic=notifications&offset=25" | jq .

echo -e "\n"

# 6. Consume delayed messages
echo "6. Consuming delayed messages..."
curl "$BASE_URL/consume?topic=delayed&maxMessages=10" | jq .

echo -e "\n=== Consumer examples completed! ===" 