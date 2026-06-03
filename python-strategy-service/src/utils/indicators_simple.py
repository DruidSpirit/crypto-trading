from typing import List, Tuple
import math


class TechnicalIndicators:
    """Simplified technical indicator calculation utility class"""

    @staticmethod
    def calculate_ema(prices: List[float], period: int) -> List[float]:
        """Calculate EMA"""
        if len(prices) < period:
            return []

        multiplier = 2 / (period + 1)
        ema_values = [prices[0]]  # First value uses original price

        for i in range(1, len(prices)):
            ema = (prices[i] * multiplier) + (ema_values[i-1] * (1 - multiplier))
            ema_values.append(ema)

        return ema_values

    @staticmethod
    def calculate_sma(prices: List[float], period: int) -> List[float]:
        """Calculate SMA"""
        if len(prices) < period:
            return []

        sma_values = []
        for i in range(period - 1, len(prices)):
            sma = sum(prices[i - period + 1:i + 1]) / period
            sma_values.append(sma)

        return sma_values

    @staticmethod
    def calculate_macd(prices: List[float], fast_period: int = 12, slow_period: int = 26, signal_period: int = 9) -> Tuple[List[float], List[float], List[float]]:
        """Calculate MACD"""
        if len(prices) < slow_period:
            return [], [], []

        # Calculate EMA
        ema12 = TechnicalIndicators.calculate_ema(prices, fast_period)
        ema26 = TechnicalIndicators.calculate_ema(prices, slow_period)

        if not ema12 or not ema26:
            return [], [], []

        # Calculate MACD line (EMA12 - EMA26)
        # EMA26 starts calculating later, need to align data
        offset = len(ema12) - len(ema26)
        macd_line = []

        for i in range(len(ema26)):
            if i + offset < len(ema12):
                macd_line.append(ema12[i + offset] - ema26[i])

        if not macd_line:
            return [], [], []

        # Calculate signal line (EMA of MACD)
        signal_line = TechnicalIndicators.calculate_ema(macd_line, signal_period)

        # Calculate histogram (MACD - Signal)
        histogram = []
        if signal_line:
            # Align MACD line and signal line
            signal_offset = len(macd_line) - len(signal_line)
            for i in range(len(signal_line)):
                if i + signal_offset < len(macd_line):
                    histogram.append(macd_line[i + signal_offset] - signal_line[i])

        return macd_line, signal_line, histogram

    @staticmethod
    def calculate_bollinger_bands(prices: List[float], period: int = 20, std_dev: int = 2) -> Tuple[List[float], List[float], List[float]]:
        """Calculate Bollinger Bands"""
        if len(prices) < period:
            return [], [], []

        sma_values = TechnicalIndicators.calculate_sma(prices, period)
        upper_band = []
        lower_band = []

        for i in range(len(sma_values)):
            # Calculate standard deviation
            price_slice = prices[i:i + period]
            mean = sum(price_slice) / period
            variance = sum((x - mean) ** 2 for x in price_slice) / period
            std = math.sqrt(variance)

            upper_band.append(sma_values[i] + (std_dev * std))
            lower_band.append(sma_values[i] - (std_dev * std))

        return upper_band, sma_values, lower_band

    @staticmethod
    def calculate_atr(highs: List[float], lows: List[float], closes: List[float], period: int = 14) -> List[float]:
        """Calculate ATR"""
        if len(highs) < period or len(lows) < period or len(closes) < period:
            return []

        true_ranges = []
        for i in range(1, len(closes)):
            tr1 = highs[i] - lows[i]
            tr2 = abs(highs[i] - closes[i-1])
            tr3 = abs(lows[i] - closes[i-1])
            true_range = max(tr1, tr2, tr3)
            true_ranges.append(true_range)

        # Calculate ATR (using simple moving average)
        atr_values = []
        for i in range(period - 1, len(true_ranges)):
            atr = sum(true_ranges[i - period + 1:i + 1]) / period
            atr_values.append(atr)

        return atr_values
