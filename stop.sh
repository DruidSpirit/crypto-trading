#!/bin/bash

# 查找运行中的 JAR 包进程并停止它
PID=$(ps -ef | grep 'crypto-trading-0.0.1-SNAPSHOT.jar' | grep -v grep | awk '{print $2}')

# 如果找到了进程，就杀掉它
if [ -n "$PID" ]; then
  kill -9 $PID
  echo "JAR process with PID $PID stopped"
else
  echo "No running JAR process found"
fi
