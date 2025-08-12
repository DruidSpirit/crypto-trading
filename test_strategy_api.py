#!/usr/bin/env python3
import requests
import json

def test_strategy_management():
    """测试策略管理功能"""
    print("=== 策略管理API测试 ===")
    print()
    
    base_url = "http://localhost:5567"
    
    # 1. 测试策略列表API
    print("1. 测试策略列表加载")
    try:
        response = requests.get(f"{base_url}/api/strategies", timeout=10)
        if response.status_code == 200:
            strategies = response.json()
            print(f"   ✓ 策略列表API成功，返回 {len(strategies)} 个策略")
            
            for strategy in strategies:
                print(f"     - ID: {strategy['id']}")
                print(f"       显示名: {strategy.get('displayName', '无')}")
                print(f"       文件名: {strategy['originalFilename']}")
                print(f"       状态: {strategy['status']}")
                print(f"       描述: {strategy.get('description', '无')}")
                print()
        else:
            print(f"   ✗ 策略列表API失败: HTTP {response.status_code}")
            print(f"     响应内容: {response.text}")
    except Exception as e:
        print(f"   ✗ 策略列表API异常: {e}")
    
    # 2. 测试信号筛选选项API (这个API为信号页面提供策略下拉选项)
    print("2. 测试信号筛选选项API")
    try:
        response = requests.get(f"{base_url}/api/select-options", timeout=10)
        if response.status_code == 200:
            options = response.json()
            strategy_names = options.get('strategyNames', [])
            print(f"   ✓ 信号筛选选项API成功，策略选项: {len(strategy_names)} 个")
            print(f"     策略名称: {strategy_names}")
        else:
            print(f"   ✗ 信号筛选选项API失败: HTTP {response.status_code}")
    except Exception as e:
        print(f"   ✗ 信号筛选选项API异常: {e}")
    
    # 3. 测试主页访问
    print("3. 测试主页访问")
    try:
        response = requests.get(f"{base_url}/", timeout=10)
        if response.status_code == 200:
            print(f"   ✓ 主页访问成功")
            # 检查页面是否包含Vue.js相关内容
            content = response.text
            if 'vue' in content.lower() or 'createapp' in content.lower():
                print("   ✓ 页面包含Vue.js内容")
            else:
                print("   ~ 页面不包含明显的Vue.js内容")
        else:
            print(f"   ✗ 主页访问失败: HTTP {response.status_code}")
    except Exception as e:
        print(f"   ✗ 主页访问异常: {e}")

if __name__ == "__main__":
    test_strategy_management()