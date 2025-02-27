@echo off
REM 停止脚本

echo Searching for Java processes...
jps -l

echo Finding the process running the JAR...
for /f "tokens=1,2 delims= " %%a in ('jps -l ^| find "crypto-trading-0.0.1-SNAPSHOT.jar"') do (
    echo Stopping process with PID %%a
    taskkill /PID %%a /F
)

if errorlevel 1 (
    echo No processes found or failed to stop the process.
) else (
    echo All processes related to the JAR have been stopped.
)

REM 暂停，防止窗口关闭
pause