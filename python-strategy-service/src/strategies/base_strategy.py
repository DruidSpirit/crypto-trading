from abc import ABC, abstractmethod
from typing import Dict, List, Optional
from src.models.dto import KlineDataDTO, TradeStrategyDTO


class BaseTradeStrategy(ABC):
    """Base trading strategy class"""

    def __init__(self):
        self.strategy_name = self.get_strategy_name()

    @abstractmethod
    def execute(self, symbol: str, kline_data: Dict[str, List[KlineDataDTO]]) -> Optional[TradeStrategyDTO]:
        """
        Execute strategy

        Args:
            symbol: Trading pair symbol
            kline_data: K-line data, key is timeframe, value is list of K-line data

        Returns:
            TradeStrategyDTO or None
        """
        pass

    @abstractmethod
    def get_strategy_name(self) -> str:
        """Get strategy name"""
        pass

    def _extract_prices(self, kline_list: List[KlineDataDTO]) -> Dict[str, List[float]]:
        """Extract price information from K-line data"""
        data = {
            'open': [],
            'high': [],
            'low': [],
            'close': [],
            'volume': [],
            'timestamps': []
        }

        for kline in kline_list:
            data['open'].append(float(kline.openPrice))
            data['high'].append(float(kline.highPrice))
            data['low'].append(float(kline.lowPrice))
            data['close'].append(float(kline.closePrice))
            data['volume'].append(float(kline.volume))
            data['timestamps'].append(kline.openTime)

        return data

    def _validate_data(self, kline_data: Dict[str, List[KlineDataDTO]]) -> bool:
        """Validate data integrity"""
        if not kline_data:
            return False

        for interval, data_list in kline_data.items():
            if not data_list or len(data_list) < 50:  # Need at least 50 data points
                return False

        return True
