@echo off
echo Starting Python Trading Strategy Service...
echo.
cd "C:\Users\druid\PycharmProjects\crypto-trading-strategy"
echo Installing/Updating Python dependencies...
pip install -r requirements.txt

echo.
echo Starting Python service on port 8000...
start "Python Strategy Service" cmd /k python run.py

echo.
echo Python service started! It should be available at: http://localhost:8000
echo You can now start your Java application.
pause