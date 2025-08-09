@echo off
echo ============================================
echo Integration Test for Crypto Trading System
echo ============================================

echo.
echo Step 1: Starting Python Strategy Service...
echo -------------------------------------------
cd /d "C:\Users\druid\PycharmProjects\crypto-trading-strategy"

REM 检查Python服务是否已经在运行
curl -s http://localhost:8000/health > nul 2>&1
if %errorlevel% == 0 (
    echo Python service is already running!
) else (
    echo Starting Python service...
    start /min "Python Strategy Service" cmd /c python run.py
    echo Waiting for Python service to start...
    timeout /t 10 /nobreak > nul
)

echo.
echo Step 2: Testing Python API...
echo -----------------------------
python test_api.py

echo.
echo Step 3: Testing Java Integration...
echo -----------------------------------
cd /d "C:\Users\druid\IdeaProjects\crypto-trading"

REM 这里可以添加Java应用的集成测试
echo Note: Start Java application manually to test full integration

echo.
echo Integration test completed!
echo ============================================
pause