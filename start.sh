#!/bin/bash

# 设置 JRE 路径
JRE_PATH="./zulu17.56.15-ca-jre17.0.14-macosx_aarch64/zulu-17.jre/Contents/Home/bin/java"

# 设置 JAR 包路径
JAR_PATH="./crypto-trading-0.0.1-SNAPSHOT.jar"

# 设置日志文件（可选）
LOG_FILE="./app.log"

# 启动 JAR 包并让它在后台运行
nohup $JRE_PATH -jar $JAR_PATH > $LOG_FILE 2>&1 &

# 输出后台进程ID
echo "JAR process started in the background with PID: $!"
