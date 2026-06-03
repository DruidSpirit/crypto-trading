#!/bin/bash
echo "==========================================="
echo " Crypto Trading - Java Backend"
echo "==========================================="
echo ""
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java not found. Please install JRE 17 or above."
    exit 1
fi
echo "Starting Java application..."
echo "Application: http://localhost:5567"
echo "Press Ctrl+C to stop."
echo ""
java -Xms256m -Xmx512m -jar crypto-trading.jar
