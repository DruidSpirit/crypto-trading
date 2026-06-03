from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
from src.strategies.base_strategy import BaseTradeStrategy
from src.models.dto import KlineDataDTO, TradeStrategyDTO
from src.utils.indicators_simple import TechnicalIndicators
import logging

logger = logging.getLogger(__name__)


class ElderSwingStrategy(BaseTradeStrategy):
    """Elder triple filter swing trading strategy (hold 3-10 days)"""

    CRYPTO_SCALE = 8

    def get_strategy_name(self) -> str:
        return "ElderSwingStrategy"

    def execute(self, symbol: str, kline_data: Dict[str, List[KlineDataDTO]]) -> Optional[TradeStrategyDTO]:
        logger.info("Starting Elder triple filter swing trading strategy...")

        # Validate data integrity
        if not self._validate_data(kline_data):
            logger.warning("K-line data incomplete or insufficient data volume")
            return None

        try:
            # Get K-line data for different timeframes
            data_1w = kline_data.get('_1W', [])  # Weekly
            data_1d = kline_data.get('_1D', [])  # Daily
            data_4h = kline_data.get('_4H', [])  # 4-hour

            if not all([data_1w, data_1d, data_4h]):
                logger.warning("Missing required K-line data (need weekly, daily, 4-hour)")
                return None

            logger.info(f"K-line data loaded: 1W={len(data_1w)} bars, 1D={len(data_1d)} bars, 4H={len(data_4h)} bars")

            # First filter: Weekly trend determination
            weekly_trend = self._analyze_weekly_trend(data_1w)
            if not weekly_trend:
                logger.info("Weekly trend unclear, strategy terminated")
                return None

            is_bullish = weekly_trend == "BUY"
            logger.info(f"First filter complete - Weekly trend: {'bullish' if is_bullish else 'bearish'}")

            # Second filter: Daily pullback signal
            daily_signal = self._analyze_daily_pullback(data_1d, is_bullish)
            if not daily_signal:
                logger.info("No pullback signal on daily level, strategy terminated")
                return None

            logger.info("Second filter complete - Daily pullback signal detected")

            # Third filter: 4-hour precise entry point
            entry_signal = self._analyze_4h_entry(data_4h, is_bullish)
            if not entry_signal:
                logger.info("No entry signal on 4-hour level, strategy terminated")
                return None

            logger.info("Third filter complete - 4-hour entry signal detected")

            # Calculate price and take profit / stop loss
            current_price = float(data_4h[-1].closePrice)
            entry_price = self._calculate_entry_price(data_4h, current_price, is_bullish)
            stop_loss_price = self._calculate_stop_loss(data_4h, entry_price, is_bullish)
            take_profit_price = self._calculate_take_profit(data_4h, entry_price, stop_loss_price, is_bullish)

            # Calculate profit/loss ratio
            profit_loss_ratio = self._calculate_profit_loss_ratio(
                entry_price, take_profit_price, stop_loss_price, is_bullish
            )

            # Validate risk/reward ratio (require at least 2:1)
            if profit_loss_ratio < 2.0:
                logger.info(f"Risk/reward ratio below 1:2 (current: {profit_loss_ratio:.2f}), strategy terminated")
                return None

            # Build trading signal
            dto = TradeStrategyDTO(
                signal="BUY" if is_bullish else "SELL",
                price=Decimal(str(current_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                buy_price=Decimal(str(entry_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                take_profit=Decimal(str(take_profit_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                stop_loss=Decimal(str(stop_loss_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                profit_loss_ratio=Decimal(str(profit_loss_ratio)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                expiration=(datetime.now() + timedelta(days=10)).strftime("%Y-%m-%d %H:%M:%S"),
                signal_time=datetime.now(),
                remark="Elder triple filter swing trading strategy"
            )

            logger.info(f"Swing trading signal generated: {dto}")
            return dto

        except Exception as e:
            logger.error(f"Strategy execution failed: {e}", exc_info=True)
            return None

    def _analyze_weekly_trend(self, data_1w: List[KlineDataDTO]) -> Optional[str]:
        """First filter: Analyze weekly trend"""
        if len(data_1w) < 30:  # Need at least 30 weeks of data
            logger.warning("Insufficient weekly data (less than 30 bars)")
            return None

        # Extract price data
        closes = [float(item.closePrice) for item in data_1w]

        # Calculate MACD
        macd_line, signal_line, histogram = TechnicalIndicators.calculate_macd(closes, 12, 26, 9)

        if len(histogram) < 3:
            logger.warning("Insufficient MACD calculation results")
            return None

        # Calculate 20-week MA
        ma20 = TechnicalIndicators.calculate_sma(closes, 20)
        if not ma20:
            logger.warning("20-week MA calculation failed")
            return None

        current_price = closes[-1]
        current_ma20 = ma20[-1]
        prev_ma20 = ma20[-2] if len(ma20) > 1 else ma20[-1]

        # Check MACD histogram trend (upward for 2+ consecutive weeks)
        recent_histogram = histogram[-3:]  # Last 3 weeks
        is_macd_bullish = all(recent_histogram[i] >= recent_histogram[i-1] for i in range(1, len(recent_histogram)))

        logger.info(f"MACD histogram trend: {'upward' if is_macd_bullish else 'unclear'}")
        logger.info(f"Current price: {current_price:.6f}, 20-week MA: {current_ma20:.6f}")
        logger.info(f"20-week MA trend: {'upward' if current_ma20 > prev_ma20 else 'downward'}")

        # Long conditions check
        if (is_macd_bullish and
            current_price > current_ma20 and
            current_ma20 > prev_ma20):
            logger.info("Weekly long conditions met")
            return "BUY"

        # Short conditions check
        is_macd_bearish = all(recent_histogram[i] <= recent_histogram[i-1] for i in range(1, len(recent_histogram)))
        if (is_macd_bearish and
            current_price < current_ma20 and
            current_ma20 < prev_ma20):
            logger.info("Weekly short conditions met")
            return "SELL"

        logger.info("Weekly trend unclear")
        return None

    def _analyze_daily_pullback(self, data_1d: List[KlineDataDTO], is_bullish: bool) -> bool:
        """Second filter: Analyze daily pullback"""
        if len(data_1d) < 50:
            logger.warning("Insufficient daily data (less than 50 bars)")
            return False

        # Extract price data
        closes = [float(item.closePrice) for item in data_1d]
        highs = [float(item.highPrice) for item in data_1d]
        lows = [float(item.lowPrice) for item in data_1d]

        # Calculate technical indicators
        stoch_k = self._calculate_stochastic(highs, lows, closes, 9, 3)
        rsi = self._calculate_rsi(closes, 14)
        ma5 = TechnicalIndicators.calculate_sma(closes, 5)
        ma10 = TechnicalIndicators.calculate_sma(closes, 10)

        if not all([stoch_k, rsi, ma5, ma10]):
            logger.warning("Technical indicator calculation failed")
            return False

        current_price = closes[-1]
        current_stoch = stoch_k[-1]
        current_rsi = rsi[-1]
        current_ma5 = ma5[-1]
        current_ma10 = ma10[-1]

        logger.info(f"Stochastic: {current_stoch:.2f}, RSI: {current_rsi:.2f}")
        logger.info(f"Price position - 5-day MA: {current_ma5:.6f}, 10-day MA: {current_ma10:.6f}")

        if is_bullish:
            # Long pullback conditions: Stochastic < 30 or RSI < 40, and price near 5-day or 10-day MA
            oversold = current_stoch < 30 or current_rsi < 40
            near_ma = abs(current_price - current_ma5) / current_ma5 < 0.02 or abs(current_price - current_ma10) / current_ma10 < 0.02

            if oversold and near_ma:
                logger.info("Long pullback conditions met")
                return True
        else:
            # Short pullback conditions: Stochastic > 70 or RSI > 60, and price near 5-day or 10-day MA
            overbought = current_stoch > 70 or current_rsi > 60
            near_ma = abs(current_price - current_ma5) / current_ma5 < 0.02 or abs(current_price - current_ma10) / current_ma10 < 0.02

            if overbought and near_ma:
                logger.info("Short pullback conditions met")
                return True

        logger.info("Daily pullback conditions not met")
        return False

    def _analyze_4h_entry(self, data_4h: List[KlineDataDTO], is_bullish: bool) -> bool:
        """Third filter: 4-hour precise entry"""
        if len(data_4h) < 10:
            logger.warning("Insufficient 4-hour data")
            return False

        # Calculate average volume
        volumes = [float(item.volume) for item in data_4h]
        volume_avg = sum(volumes[-5:]) / 5  # 5-period average volume
        current_volume = volumes[-1]

        # Check volume condition
        volume_condition = current_volume > volume_avg

        # Check breakout condition
        if len(data_4h) >= 2:
            prev_high = float(data_4h[-2].highPrice)
            prev_low = float(data_4h[-2].lowPrice)
            current_high = float(data_4h[-1].highPrice)
            current_low = float(data_4h[-1].lowPrice)

            if is_bullish:
                # Long: Break above previous 4-hour high with volume increase
                breakthrough = current_high > prev_high
            else:
                # Short: Break below previous 4-hour low with volume increase
                breakthrough = current_low < prev_low

            entry_signal = breakthrough and volume_condition
            logger.info(f"Entry signal: breakout={'yes' if breakthrough else 'no'}, volume increase={'yes' if volume_condition else 'no'}")

            return entry_signal

        return False

    def _calculate_entry_price(self, data_4h: List[KlineDataDTO], current_price: float, is_bullish: bool) -> float:
        """Calculate entry price"""
        if is_bullish:
            # Long: Previous 4-hour high + 0.01%
            prev_high = float(data_4h[-2].highPrice) if len(data_4h) >= 2 else current_price
            entry_price = prev_high * 1.0001
        else:
            # Short: Previous 4-hour low - 0.01%
            prev_low = float(data_4h[-2].lowPrice) if len(data_4h) >= 2 else current_price
            entry_price = prev_low * 0.9999

        logger.info(f"Calculated entry price: {entry_price:.6f}")
        return entry_price

    def _calculate_stop_loss(self, data_4h: List[KlineDataDTO], entry_price: float, is_bullish: bool) -> float:
        """Calculate stop loss price"""
        # Extract price data
        closes = [float(item.closePrice) for item in data_4h]
        highs = [float(item.highPrice) for item in data_4h]
        lows = [float(item.lowPrice) for item in data_4h]

        # Calculate ATR
        atr_values = TechnicalIndicators.calculate_atr(highs, lows, closes, 14)

        if is_bullish:
            # Long stop loss: take the highest of three methods
            # 1. Below the lowest point of last 5 4-hour bars by 0.2%
            recent_low = min(lows[-5:])
            support_stop = recent_low * 0.998

            # 2. Below 10-period MA by 0.5%
            ma10 = TechnicalIndicators.calculate_sma(closes, 10)
            ma_stop = ma10[-1] * 0.995 if ma10 else entry_price * 0.98

            # 3. ATR stop loss: entry price - 1.5x ATR
            atr_stop = entry_price - (atr_values[-1] * 1.5) if atr_values else entry_price * 0.98

            stop_loss = max(support_stop, ma_stop, atr_stop)
        else:
            # Short stop loss: take the lowest of three methods
            # 1. Above the highest point of last 5 4-hour bars by 0.2%
            recent_high = max(highs[-5:])
            resistance_stop = recent_high * 1.002

            # 2. Above 10-period MA by 0.5%
            ma10 = TechnicalIndicators.calculate_sma(closes, 10)
            ma_stop = ma10[-1] * 1.005 if ma10 else entry_price * 1.02

            # 3. ATR stop loss: entry price + 1.5x ATR
            atr_stop = entry_price + (atr_values[-1] * 1.5) if atr_values else entry_price * 1.02

            stop_loss = min(resistance_stop, ma_stop, atr_stop)

        # Ensure stop loss range is between 1.5%-3%
        if is_bullish:
            max_stop = entry_price * 0.985  # 1.5%
            min_stop = entry_price * 0.97   # 3%
            stop_loss = max(min_stop, min(stop_loss, max_stop))
        else:
            max_stop = entry_price * 1.015  # 1.5%
            min_stop = entry_price * 1.03   # 3%
            stop_loss = min(min_stop, max(stop_loss, max_stop))

        logger.info(f"Calculated stop loss price: {stop_loss:.6f}")
        return stop_loss

    def _calculate_take_profit(self, data_4h: List[KlineDataDTO], entry_price: float, stop_loss: float, is_bullish: bool) -> float:
        """Calculate take profit price (2:1 risk/reward ratio)"""
        if is_bullish:
            risk = entry_price - stop_loss
            take_profit = entry_price + (risk * 2)  # 2:1 reward/risk ratio
        else:
            risk = stop_loss - entry_price
            take_profit = entry_price - (risk * 2)  # 2:1 reward/risk ratio

        logger.info(f"Calculated take profit price: {take_profit:.6f}")
        return take_profit

    def _calculate_profit_loss_ratio(self, entry_price: float, take_profit: float, stop_loss: float, is_bullish: bool) -> float:
        """Calculate profit/loss ratio"""
        if is_bullish:
            profit = take_profit - entry_price
            loss = entry_price - stop_loss
        else:
            profit = entry_price - take_profit
            loss = stop_loss - entry_price

        if loss <= 0:
            return 0.0

        ratio = profit / loss
        logger.info(f"Profit/loss ratio: profit={profit:.6f}, loss={loss:.6f}, ratio={ratio:.2f}")
        return ratio

    def _calculate_stochastic(self, highs: List[float], lows: List[float], closes: List[float],
                            k_period: int = 9, d_period: int = 3) -> List[float]:
        """Calculate Stochastic %K values"""
        if len(highs) < k_period or len(lows) < k_period or len(closes) < k_period:
            return []

        k_values = []
        for i in range(k_period - 1, len(closes)):
            high_max = max(highs[i - k_period + 1:i + 1])
            low_min = min(lows[i - k_period + 1:i + 1])

            if high_max == low_min:
                k_value = 50  # Avoid division by zero
            else:
                k_value = ((closes[i] - low_min) / (high_max - low_min)) * 100

            k_values.append(k_value)

        # Smooth %K values with d_period
        smooth_k = TechnicalIndicators.calculate_sma(k_values, d_period)
        return smooth_k

    def _calculate_rsi(self, prices: List[float], period: int = 14) -> List[float]:
        """Calculate RSI indicator"""
        if len(prices) < period + 1:
            return []

        gains = []
        losses = []

        for i in range(1, len(prices)):
            change = prices[i] - prices[i-1]
            if change > 0:
                gains.append(change)
                losses.append(0)
            else:
                gains.append(0)
                losses.append(abs(change))

        if len(gains) < period:
            return []

        rsi_values = []
        for i in range(period - 1, len(gains)):
            avg_gain = sum(gains[i - period + 1:i + 1]) / period
            avg_loss = sum(losses[i - period + 1:i + 1]) / period

            if avg_loss == 0:
                rsi = 100
            else:
                rs = avg_gain / avg_loss
                rsi = 100 - (100 / (1 + rs))

            rsi_values.append(rsi)

        return rsi_values
