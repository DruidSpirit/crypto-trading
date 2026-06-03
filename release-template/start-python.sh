#!/bin/bash
echo "==========================================="
echo " Crypto Trading - Python Strategy Service"
echo "==========================================="
echo ""
if ! command -v python3 &> /dev/null && ! command -v python &> /dev/null; then
    echo "[ERROR] Python not found. Please install Python 3.9 or above."
    exit 1
fi
cd "$(dirname "$0")/python-strategy-service" || { echo "Directory not found"; exit 1; }
echo "Installing dependencies..."
pip install -r requirements.txt
echo ""
echo "Starting Python service on port 8001..."
echo "Press Ctrl+C to stop."
echo ""
PORT=8001 python run.py
