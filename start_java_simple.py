import subprocess
import sys
import time
import os

def start_java_app():
    """启动Java应用程序"""
    print("正在启动Java应用程序...")
    
    # 确保在正确的目录
    os.chdir(r"C:\Users\druid\IdeaProjects\crypto-trading")
    
    try:
        # 使用mvnw.cmd启动Spring Boot应用
        process = subprocess.Popen([
            "mvnw.cmd", "spring-boot:run", 
            "-Dspring-boot.run.jvmArguments=-Dserver.port=5567"
        ], shell=True)
        
        print(f"Java应用程序已启动，PID: {process.pid}")
        print("请等待应用程序启动完成...")
        print("可以访问 http://localhost:5567 查看应用")
        
        # 等待用户输入来停止应用
        input("按回车键停止应用程序...")
        process.terminate()
        
    except Exception as e:
        print(f"启动失败: {e}")

if __name__ == "__main__":
    start_java_app()