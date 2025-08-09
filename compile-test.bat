@echo off
echo Checking if Spring Boot application can start...

REM 尝试使用Maven Wrapper运行
if exist "mvnw.cmd" (
    echo Using Maven Wrapper...
    call mvnw.cmd clean compile spring-boot:run -DskipTests
) else (
    echo Maven Wrapper not found. Trying direct Java compilation...
    echo Note: This is a limited test. For full compilation, Maven is required.
    
    REM 创建classes目录
    if not exist "target\classes" mkdir target\classes
    
    REM 尝试基本的Java编译（需要classpath）
    echo This project requires Maven for full compilation due to Spring Boot dependencies.
    echo Please install Maven or use an IDE with Maven support.
)

echo.
echo If compilation succeeds, the application will start.
echo Press Ctrl+C to stop the application when ready.
pause