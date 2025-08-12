#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import requests
import json
import time

def test_python_service():
    """测试Python服务"""
    print("=== 测试Python服务 (端口8001) ===")
    
    try:
        # 测试根路径
        response = requests.get("http://localhost:8001/", timeout=5)
        print(f"✓ Python根路径: {response.status_code} - {response.json().get('message', '')}")
        
        # 测试策略列表
        response = requests.get("http://localhost:8001/api/strategy/strategies", timeout=5)
        if response.status_code == 200:
            strategies = response.json().get('data', [])
            print(f"✓ 策略列表API: 返回 {len(strategies)} 个策略: {strategies}")
        else:
            print(f"✗ 策略列表API: {response.status_code}")
            
        # 测试回测数据信息API
        response = requests.get("http://localhost:8001/api/backtest/data-info", timeout=10)
        if response.status_code == 200:
            data_info = response.json()
            print(f"✓ 回测数据信息API: {data_info.get('message', '')}")
        else:
            print(f"✗ 回测数据信息API: {response.status_code}")
            
        # 测试批量下载API端点可达性
        response = requests.post("http://localhost:8001/api/backtest/batch-download", 
                               json={
                                   "symbols": ["BTCUSDT"], 
                                   "start_date": "2023-01-01",
                                   "end_date": "2023-01-02",
                                   "timeframe": "1h"
                               }, timeout=30)
        if response.status_code == 200:
            result = response.json()
            print(f"✓ 批量下载API: {result.get('message', '')}")
        else:
            print(f"~ 批量下载API: {response.status_code} (可能需要更长时间)")
            
    except Exception as e:
        print(f"✗ Python服务错误: {e}")
        
def test_java_service():
    """测试Java服务"""
    print("\n=== 测试Java服务 (端口5567) ===")
    
    # 先检查Java进程
    import subprocess
    try:
        result = subprocess.run(['tasklist', '/FI', 'IMAGENAME eq java.exe'], 
                              capture_output=True, text=True, shell=True)
        if 'java.exe' in result.stdout:
            print("✓ Java进程运行中")
        else:
            print("✗ Java进程未运行")
            return
    except:
        pass
        
    try:
        # 测试策略管理API
        response = requests.get("http://localhost:5567/api/strategies", timeout=10)
        if response.status_code == 200:
            result = response.json()
            if result.get('success'):
                strategies = result.get('data', [])
                print(f"✓ Java策略列表API: 返回 {len(strategies)} 个策略")
            else:
                print(f"✗ Java策略列表API: {result.get('message', '')}")
        else:
            print(f"✗ Java策略列表API: HTTP {response.status_code}")
            
        # 测试回测功能API  
        response = requests.get("http://localhost:5567/api/backtest/data-info", timeout=10)
        if response.status_code == 200:
            result = response.json()
            print(f"✓ Java回测数据API: {result.get('message', 'Success')}")
        else:
            print(f"✗ Java回测数据API: HTTP {response.status_code}")
            
        # 测试批量下载API
        response = requests.post("http://localhost:5567/api/backtest/batch-download",
                               json={
                                   "symbols": ["BTCUSDT"],
                                   "startDate": "2023-01-01", 
                                   "endDate": "2023-01-02",
                                   "timeframe": "1h"
                               }, timeout=30)
        if response.status_code == 200:
            result = response.json()
            print(f"✓ Java批量下载API: {result.get('message', 'Success')}")
        else:
            print(f"~ Java批量下载API: HTTP {response.status_code} (可能需要Python服务支持)")
            
    except requests.exceptions.ConnectError:
        print("✗ 无法连接到Java服务 (端口5567)")
    except requests.exceptions.Timeout:
        print("✗ Java服务响应超时")
    except Exception as e:
        print(f"✗ Java服务错误: {e}")

def main():
    print("开始测试所有功能...")
    print("=" * 50)
    
    # 测试Python服务
    test_python_service()
    
    # 测试Java服务
    test_java_service()
    
    print("\n=== 测试总结 ===")
    print("如果所有项目都显示 ✓，说明功能修复成功")
    print("如果有 ✗ 项目，需要进一步排查问题")
    print("~ 符号表示功能可达但可能需要额外配置")

if __name__ == "__main__":
    main()