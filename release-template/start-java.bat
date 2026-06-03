@echo off
title Crypto Trading - Java Backend
echo ===========================================
echo  Crypto Trading - Java Backend
echo ===========================================
echo.
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Please install JRE 17 or above.
    pause
    exit /b 1
)
echo Starting Java application...
echo Application: http://localhost:5567
echo Close this window to stop.
echo.
java -Xms256m -Xmx512m -jar crypto-trading.jar
pause
