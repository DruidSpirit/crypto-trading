@echo off
echo "Starting Crypto Trading Services..."

echo "Starting Python Strategy Service..."
start "Python Strategy Service" cmd /k "cd /d C:\Users\druid\PycharmProjects\crypto-trading-strategy && python start.py"

timeout /t 5

echo "Starting Java Trading Application..."
start "Java Trading App" cmd /k "cd /d C:\Users\druid\IdeaProjects\crypto-trading && mvn spring-boot:run"

echo "All services started!"
echo "Python Service: http://localhost:8001"
echo "Java Service: http://localhost:5567"
pause