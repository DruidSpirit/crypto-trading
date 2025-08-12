"""
测试策略文件 - 简单移动平均策略

这是一个用于测试策略热更新功能的示例Python策略文件。
实现了基于移动平均线的交易策略。
"""

import numpy as np
import pandas as pd
from typing import Dict, List, Optional


class SimpleMovingAverageStrategy:
    """简单移动平均策略"""
    
    def __init__(self, short_period: int = 10, long_period: int = 20):
        """
        初始化策略参数
        
        Args:
            short_period: 短期移动平均周期
            long_period: 长期移动平均周期
        """
        self.short_period = short_period
        self.long_period = long_period
        self.name = "SimpleMovingAverageStrategy"
        self.version = "1.0.0"
    
    def calculate_indicators(self, prices: List[float]) -> Dict[str, float]:
        """
        计算技术指标
        
        Args:
            prices: 价格序列
            
        Returns:
            包含技术指标的字典
        """
        if len(prices) < self.long_period:
            return {}
        
        prices_array = np.array(prices)
        
        # 计算短期和长期移动平均
        short_ma = np.mean(prices_array[-self.short_period:])
        long_ma = np.mean(prices_array[-self.long_period:])
        
        return {
            'short_ma': short_ma,
            'long_ma': long_ma,
            'current_price': prices_array[-1]
        }
    
    def generate_signal(self, indicators: Dict[str, float]) -> Optional[str]:
        """
        根据技术指标生成交易信号
        
        Args:
            indicators: 技术指标字典
            
        Returns:
            交易信号: 'BUY', 'SELL', 或 None
        """
        if not indicators:
            return None
        
        short_ma = indicators.get('short_ma')
        long_ma = indicators.get('long_ma')
        
        if short_ma is None or long_ma is None:
            return None
        
        # 简单的黄金交叉和死亡交叉策略
        if short_ma > long_ma * 1.01:  # 短期均线突破长期均线1%以上
            return 'BUY'
        elif short_ma < long_ma * 0.99:  # 短期均线跌破长期均线1%以下
            return 'SELL'
        
        return None
    
    def run_strategy(self, symbol: str, prices: List[float]) -> Dict[str, any]:
        """
        运行策略
        
        Args:
            symbol: 交易对符号
            prices: 价格序列
            
        Returns:
            策略运行结果
        """
        indicators = self.calculate_indicators(prices)
        signal = self.generate_signal(indicators)
        
        return {
            'symbol': symbol,
            'strategy': self.name,
            'version': self.version,
            'signal': signal,
            'indicators': indicators,
            'timestamp': pd.Timestamp.now().isoformat()
        }


def create_strategy():
    """策略工厂方法"""
    return SimpleMovingAverageStrategy(short_period=10, long_period=20)


# 策略测试代码
if __name__ == "__main__":
    # 创建策略实例
    strategy = create_strategy()
    
    # 模拟价格数据
    test_prices = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109,
                   110, 111, 112, 113, 114, 115, 116, 117, 118, 119,
                   120, 121, 122, 123, 124, 125]
    
    # 运行策略
    result = strategy.run_strategy("BTCUSDT", test_prices)
    print(f"策略运行结果: {result}")