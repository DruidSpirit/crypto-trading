import requests

print("=== 策略管理最终验证 ===")

# 测试策略API
try:
    response = requests.get("http://localhost:5567/api/strategies", timeout=10)
    if response.status_code == 200:
        strategies = response.json()
        print(f"策略API: 成功返回 {len(strategies)} 个策略")
        for strategy in strategies:
            print(f"  - {strategy.get('displayName', 'N/A')} (ID: {strategy['id']})")
    else:
        print(f"策略API: 失败，状态码 {response.status_code}")
except Exception as e:
    print(f"策略API: 异常 {e}")

# 测试主页访问
try:
    response = requests.get("http://localhost:5567/", timeout=10)
    if response.status_code == 200:
        print("主页访问: 成功")
        content = response.text
        if 'Vue' in content or 'createApp' in content:
            print("  - 包含Vue.js代码")
        if 'loadStrategies' in content:
            print("  - 包含策略加载代码")
    else:
        print(f"主页访问: 失败，状态码 {response.status_code}")
except Exception as e:
    print(f"主页访问: 异常 {e}")

print("\n验证完成！")