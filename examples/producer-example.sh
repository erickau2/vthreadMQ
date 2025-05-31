#!/bin/bash

# VthreadMQ Producer Examples
# Make sure VthreadMQ is running on localhost:8080

BASE_URL="http://localhost:8080/api"

echo "=== VthreadMQ Producer Examples ==="

# 1. Basic message production
echo "1. Producing basic message..."
curl -X POST "$BASE_URL/produce" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "notifications",
    "content": "Hello from VthreadMQ!",
    "maxRetries": 3
  }' | jq .

echo -e "\n"

# 2. Delayed message (5 seconds)
echo "2. Producing delayed message (5 seconds)..."
curl -X GET "$BASE_URL/produce?topic=delayed&content=This%20message%20was%20delayed&delaySec=5" | jq .

echo -e "\n"

# 3. Email message with headers
echo "3. Producing email message..."
curl -X POST "$BASE_URL/produce" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "email",
    "content": "Your order #12345 has been shipped and is on its way!",
    "headers": {
      "to": "customer@example.com",
      "subject": "Order Shipped - #12345"
    },
    "maxRetries": 5
  }' | jq .

echo -e "\n"

# 4. Scheduled message for future
echo "4. Producing scheduled message..."
FUTURE_TIME=$(date -d "+1 minute" -u +"%Y-%m-%dT%H:%M:%SZ")
curl -X POST "$BASE_URL/produce" \
  -H "Content-Type: application/json" \
  -d "{
    \"topic\": \"scheduled\",
    \"content\": \"This message was scheduled for $FUTURE_TIME\",
    \"scheduledAt\": \"$FUTURE_TIME\"
  }" | jq .

echo -e "\n"

# 5. Batch produce multiple messages
echo "5. Producing multiple messages..."
for i in {1..5}; do
  curl -X GET "$BASE_URL/produce?topic=batch&content=Batch%20message%20$i" > /dev/null 2>&1
  echo "Produced message $i"
done

echo -e "\n=== All messages produced successfully! ===" 