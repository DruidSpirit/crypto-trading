"""
RSI策略 - 基于相对强弱指数的交易策略

这是一个基于RSI指标的Python策略文件，用于测试策略管理功能。
"""

import numpy as np
from typing import Dict, List, Optional


class RSIStrategy:
    """RSI相对强弱指数策略"""
    
    def __init__(self, period: int = 14, oversold: float = 30, overbought: float = 70):
        """
        初始化RSI策略参数
        
        Args:
            period: RSI计算周期
            oversold: 超卖阈值
            overbought: 超买阈值
        """
        self.period = period
        self.oversold = oversold
        self.overbought = overbought
        self.name = "RSIStrategy"
        self.version = "1.0.0"
    
    def calculate_rsi(self, prices: List[float]) -> Optional[float]:
        """
        计算RSI指标
        
        Args:
            prices: 价格序列
            
        Returns:
            RSI值
        """
        if len(prices) < self.period + 1:
            return None
        
        deltas = np.diff(prices)
        gains = np.where(deltas > 0, deltas, 0)
        losses = np.where(deltas < 0, -deltas, 0)
        
        avg_gain = np.mean(gains[-self.period:])
        avg_loss = np.mean(losses[-self.period:])
        
        if avg_loss == 0:
            return 100
        
        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))
        
        return rsi
    
    def generate_signal(self, prices: List[float]) -> Dict[str, any]:
        """
        生成交易信号
        
        Args:
            prices: 价格序列
            
        Returns:
            交易信号和指标信息
        """
        rsi = self.calculate_rsi(prices)
        
        if rsi is None:
            return {
                'signal': None,
                'rsi': None,
                'reason': 'Insufficient data'
            }
        
        signal = None
        reason = 'Hold'
        
        if rsi <= self.oversold:
            signal = 'BUY'
            reason = f'RSI oversold: {rsi:.2f}'
        elif rsi >= self.overbought:
            signal = 'SELL'
            reason = f'RSI overbought: {rsi:.2f}'
        
        return {
            'signal': signal,
            'rsi': rsi,
            'reason': reason
        }


def create_strategy():
    """策略工厂方法"""
    return RSIStrategy(period=14, oversold=30, overbought=70)


# 测试代码
if __name__ == "__main__":
    strategy = create_strategy()
    
    # 模拟价格数据 - 创造一个超卖后反弹的场景
    test_prices = [100, 95, 90, 85, 80, 75, 70, 65, 60, 55,
                   50, 48, 46, 44, 42, 40, 45, 50, 55, 60]
    
    result = strategy.generate_signal(test_prices)
    print(f"RSI策略结果: {result}")