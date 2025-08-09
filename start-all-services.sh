#!/bin/bash

echo "Starting Crypto Trading Services..."

echo "Starting Python Strategy Service..."
cd "C:/Users/druid/PycharmProjects/crypto-trading-strategy"
python start.py &
PYTHON_PID=$!

sleep 5

echo "Starting Java Trading Application..."
cd "C:/Users/druid/IdeaProjects/crypto-trading"
mvn spring-boot:run &
JAVA_PID=$!

echo "All services started!"
echo "Python Service: http://localhost:8001 (PID: $PYTHON_PID)"
echo "Java Service: http://localhost:5567 (PID: $JAVA_PID)"

# Wait for both processes
wait $PYTHON_PID
wait $JAVA_PID