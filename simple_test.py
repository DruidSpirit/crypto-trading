import requests

print("Testing Python service on port 8001...")
try:
    response = requests.get("http://localhost:8001/", timeout=5)
    print("Python service root:", response.status_code, response.json().get('message', ''))
    
    response = requests.get("http://localhost:8001/api/strategy/strategies", timeout=5)
    print("Python strategies API:", response.status_code)
    if response.status_code == 200:
        strategies = response.json().get('data', [])
        print("Available strategies:", strategies)
        
    response = requests.get("http://localhost:8001/api/backtest/data-info", timeout=10)
    print("Python backtest API:", response.status_code)
    
except Exception as e:
    print("Python service error:", str(e))

print("\nTesting Java service on port 5567...")
try:
    response = requests.get("http://localhost:5567/api/strategies", timeout=10)
    print("Java strategies API:", response.status_code)
    if response.status_code == 200:
        result = response.json()
        print("Java strategies success:", result.get('success', False))
        
    response = requests.get("http://localhost:5567/api/backtest/data-info", timeout=10)
    print("Java backtest API:", response.status_code)
    
except Exception as e:
    print("Java service error:", str(e))