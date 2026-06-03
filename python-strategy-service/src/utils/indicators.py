import pandas as pd
import numpy as np
import ta
from typing import Tuple


class TechnicalIndicators:
    """Technical indicator calculation utility class"""

    @staticmethod
    def calculate_macd(df: pd.DataFrame, fast_period: int = 12, slow_period: int = 26, signal_period: int = 9) -> Tuple[pd.Series, pd.Series, pd.Series]:
        """
        Calculate MACD indicator

        Args:
            df: DataFrame containing 'close' column
            fast_period: Fast line period
            slow_period: Slow line period
            signal_period: Signal line period

        Returns:
            Tuple[macd, signal, histogram]
        """
        macd_line = ta.trend.MACD(df['close'], window_fast=fast_period, window_slow=slow_period).macd()
        signal_line = ta.trend.MACD(df['close'], window_fast=fast_period, window_slow=slow_period).macd_signal()
        histogram = macd_line - signal_line

        return macd_line, signal_line, histogram

    @staticmethod
    def calculate_bollinger_bands(df: pd.DataFrame, period: int = 20, std_dev: int = 2) -> Tuple[pd.Series, pd.Series, pd.Series]:
        """
        Calculate Bollinger Bands indicator

        Args:
            df: DataFrame containing 'close' column
            period: Period
            std_dev: Standard deviation multiplier

        Returns:
            Tuple[upper, middle, lower]
        """
        bb = ta.volatility.BollingerBands(df['close'], window=period, window_dev=std_dev)
        upper = bb.bollinger_hband()
        middle = bb.bollinger_mavg()
        lower = bb.bollinger_lband()

        return upper, middle, lower

    @staticmethod
    def calculate_atr(df: pd.DataFrame, period: int = 14) -> pd.Series:
        """
        Calculate ATR indicator

        Args:
            df: DataFrame containing 'high', 'low', 'close' columns
            period: Period

        Returns:
            ATR value series
        """
        return ta.volatility.AverageTrueRange(df['high'], df['low'], df['close'], window=period).average_true_range()

    @staticmethod
    def calculate_ema(df: pd.DataFrame, period: int) -> pd.Series:
        """
        Calculate EMA indicator

        Args:
            df: DataFrame containing 'close' column
            period: Period

        Returns:
            EMA value series
        """
        return ta.trend.EMAIndicator(df['close'], window=period).ema_indicator()

    @staticmethod
    def calculate_sma(df: pd.DataFrame, period: int) -> pd.Series:
        """
        Calculate SMA indicator

        Args:
            df: DataFrame containing 'close' column
            period: Period

        Returns:
            SMA value series
        """
        return ta.trend.SMAIndicator(df['close'], window=period).sma_indicator()
