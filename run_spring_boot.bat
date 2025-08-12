@echo off
cd /d "C:\Users\druid\IdeaProjects\crypto-trading"
echo Starting Spring Boot application...
mvnw.cmd clean compile
mvnw.cmd spring-boot:run
pause