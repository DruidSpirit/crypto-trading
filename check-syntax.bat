@echo off
echo Checking Java syntax without dependencies...

echo Checking DTO files...
javac -cp . src\main\java\druid\elf\tool\dto\*.java 2>nul && echo "DTO files: OK" || echo "DTO files: Syntax errors (expected - needs lombok)"

echo.
echo Syntax check completed. Maven is needed for full compilation with dependencies.
pause