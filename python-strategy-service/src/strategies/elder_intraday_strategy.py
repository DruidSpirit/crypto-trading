from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
from src.strategies.base_strategy import BaseTradeStrategy
from src.models.dto import KlineDataDTO, TradeStrategyDTO
from src.utils.indicators_simple import TechnicalIndicators
import logging

logger = logging.getLogger(__name__)


class ElderIntradayStrategy(BaseTradeStrategy):
    """Elder triple filter intraday strategy (close same day)"""

    CRYPTO_SCALE = 8

    def get_strategy_name(self) -> str:
        return "ElderIntradayStrategy"

    def execute(self, symbol: str, kline_data: Dict[str, List[KlineDataDTO]]) -> Optional[TradeStrategyDTO]:
        logger.info("Starting Elder triple filter intraday strategy...")

        # Validate data integrity
        if not self._validate_data(kline_data):
            logger.warning("K-line data incomplete or insufficient data volume")
            return None

        try:
            # Get K-line data for different timeframes
            data_1d = kline_data.get('_1D', [])   # Daily
            data_30m = kline_data.get('_30M', []) # 30-minute
            data_5m = kline_data.get('_5M', [])   # 5-minute

            if not all([data_1d, data_30m, data_5m]):
                logger.warning("Missing required K-line data (need daily, 30-minute, 5-minute)")
                return None

            logger.info(f"K-line data loaded: 1D={len(data_1d)} bars, 30M={len(data_30m)} bars, 5M={len(data_5m)} bars")

            # First filter: Daily direction determination
            daily_direction = self._analyze_daily_direction(data_1d)
            if not daily_direction:
                logger.info("Daily direction unclear, strategy terminated")
                return None

            is_bullish = daily_direction == "BUY"
            logger.info(f"First filter complete - Daily direction: {'bullish' if is_bullish else 'bearish'}")

            # Second filter: 30-minute pullback signal
            pullback_signal = self._analyze_30m_pullback(data_30m, is_bullish)
            if not pullback_signal:
                logger.info("No pullback signal on 30-minute level, strategy terminated")
                return None

            logger.info("Second filter complete - 30-minute pullback signal detected")

            # Third filter: 5-minute precise entry point
            entry_signal = self._analyze_5m_entry(data_5m, is_bullish)
            if not entry_signal:
                logger.info("No entry signal on 5-minute level, strategy terminated")
                return None

            logger.info("Third filter complete - 5-minute entry signal detected")

            # Calculate price and take profit / stop loss
            current_price = float(data_5m[-1].closePrice)
            entry_price = current_price  # Intraday strategy enters at current price
            stop_loss_price = self._calculate_intraday_stop_loss(data_30m, entry_price, is_bullish)
            take_profit_price = self._calculate_intraday_take_profit(entry_price, stop_loss_price, is_bullish)

            # Calculate profit/loss ratio
            profit_loss_ratio = self._calculate_profit_loss_ratio(
                entry_price, take_profit_price, stop_loss_price, is_bullish
            )

            # Validate risk/reward ratio (intraday minimum 1.5:1)
            if profit_loss_ratio < 1.5:
                logger.info(f"Risk/reward ratio below 1:1.5 (current: {profit_loss_ratio:.2f}), strategy terminated")
                return None

            # Build trading signal (intraday strategy expires same day)
            expiration_time = datetime.now().replace(hour=15, minute=0, second=0, microsecond=0)
            if datetime.now() >= expiration_time:
                expiration_time += timedelta(days=1)

            dto = TradeStrategyDTO(
                signal="BUY" if is_bullish else "SELL",
                price=Decimal(str(current_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                buy_price=Decimal(str(entry_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                take_profit=Decimal(str(take_profit_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                stop_loss=Decimal(str(stop_loss_price)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                profit_loss_ratio=Decimal(str(profit_loss_ratio)).quantize(Decimal('0.00000001'), rounding=ROUND_HALF_UP),
                expiration=expiration_time.strftime("%Y-%m-%d %H:%M:%S"),
                signal_time=datetime.now(),
                remark="Elder triple filter intraday strategy"
            )

            logger.info(f"Intraday trading signal generated: {dto}")
            return dto

        except Exception as e:
            logger.error(f"Strategy execution failed: {e}", exc_info=True)
            return None

    def _analyze_daily_direction(self, data_1d: List[KlineDataDTO]) -> Optional[str]:
        """First filter: Analyze daily direction"""
        if len(data_1d) < 30:  # Need at least 30 days of data
            logger.warning("Insufficient daily data (less than 30 bars)")
            return None

        # Extract price data
        closes = [float(item.closePrice) for item in data_1d]
        opens = [float(item.openPrice) for item in data_1d]

        # Calculate MACD
        macd_line, signal_line, histogram = TechnicalIndicators.calculate_macd(closes, 12, 26, 9)

        if len(histogram) < 2:
            logger.warning("Insufficient MACD calculation results")
            return None

        # Calculate 5-day MA
        ma5 = TechnicalIndicators.calculate_sma(closes, 5)
        if not ma5:
            logger.warning("5-day MA calculation failed")
            return None

        current_price = closes[-1]
        current_ma5 = ma5[-1]
        current_macd = macd_line[-1]
        current_signal = signal_line[-1]
        current_histogram = histogram[-1]

        # Check yesterday's candle type
        yesterday_open = opens[-1]
        yesterday_close = closes[-1]
        is_bullish_candle = yesterday_close > yesterday_open
        is_doji = abs(yesterday_close - yesterday_open) / yesterday_open < 0.005  # 0.5% considered doji

        logger.info(f"MACD line: {current_macd:.6f}, signal line: {current_signal:.6f}")
        logger.info(f"Current price: {current_price:.6f}, 5-day MA: {current_ma5:.6f}")
        logger.info(f"Yesterday candle: {'bullish' if is_bullish_candle else 'bearish' if not is_doji else 'doji'}")

        # Long conditions: MACD above zero with histogram upward, price above 5-day MA, yesterday bullish or doji
        if (current_macd > 0 and
            current_histogram > 0 and
            current_price > current_ma5 and
            (is_bullish_candle or is_doji)):
            logger.info("Daily long conditions met")
            return "BUY"

        # Short conditions: MACD below zero with histogram downward, price below 5-day MA, yesterday bearish
        if (current_macd < 0 and
            current_histogram < 0 and
            current_price < current_ma5 and
            not is_bullish_candle and not is_doji):
            logger.info("Daily short conditions met")
            return "SELL"

        logger.info("Daily direction unclear")
        return None

    def _analyze_30m_pullback(self, data_30m: List[KlineDataDTO], is_bullish: bool) -> bool:
        """Second filter: Analyze 30-minute pullback"""
        if len(data_30m) < 50:
            logger.warning("Insufficient 30-minute data (less than 50 bars)")
            return False

        # Extract price data
        closes = [float(item.closePrice) for item in data_30m]
        highs = [float(item.highPrice) for item in data_30m]
        lows = [float(item.lowPrice) for item in data_30m]

        # Calculate technical indicators
        stoch_k = self._calculate_stochastic(highs, lows, closes, 14, 3)
        ma20 = TechnicalIndicators.calculate_sma(closes, 20)

        if not all([stoch_k, ma20]):
            logger.warning("30-minute technical indicator calculation failed")
            return False

        current_price = closes[-1]
        current_stoch = stoch_k[-1]
        current_ma20 = ma20[-1]

        # Check if price has broken through important support/resistance levels
        recent_highs = highs[-20:]
        recent_lows = lows[-20:]
        resistance_level = max(recent_highs)
        support_level = min(recent_lows)

        logger.info(f"30-minute Stochastic: {current_stoch:.2f}")
        logger.info(f"Price: {current_price:.6f}, 20 MA: {current_ma20:.6f}")
        logger.info(f"Support level: {support_level:.6f}, resistance level: {resistance_level:.6f}")

        if is_bullish:
            # Long pullback conditions: Stochastic below 40, price near MA20, above support level
            oversold = current_stoch < 40
            near_ma20 = abs(current_price - current_ma20) / current_ma20 < 0.015  # Within 1.5%
            above_support = current_price > support_level * 1.005  # 0.5% above support

            if oversold and near_ma20 and above_support:
                logger.info("30-minute long pullback conditions met")
                return True
        else:
            # Short pullback conditions: Stochastic above 60, price near MA20, below resistance level
            overbought = current_stoch > 60
            near_ma20 = abs(current_price - current_ma20) / current_ma20 < 0.015  # Within 1.5%
            below_resistance = current_price < resistance_level * 0.995  # 0.5% below resistance

            if overbought and near_ma20 and below_resistance:
                logger.info("30-minute short pullback conditions met")
                return True

        logger.info("30-minute pullback conditions not met")
        return False

    def _analyze_5m_entry(self, data_5m: List[KlineDataDTO], is_bullish: bool) -> bool:
        """Third filter: 5-minute precise entry"""
        if len(data_5m) < 10:
            logger.warning("Insufficient 5-minute data")
            return False

        # Extract recent candle data
        recent_candles = data_5m[-5:]  # Last 5 candles
        volumes = [float(item.volume) for item in data_5m]
        current_volume = volumes[-1]
        avg_volume = sum(volumes[-10:]) / 10  # 10-period average volume

        # Check volume increase
        volume_surge = current_volume > avg_volume * 1.2  # Volume increase by 20%+

        # Analyze candle pattern
        current_candle = recent_candles[-1]
        prev_candle = recent_candles[-2] if len(recent_candles) >= 2 else None

        current_open = float(current_candle.openPrice)
        current_close = float(current_candle.closePrice)
        current_high = float(current_candle.highPrice)
        current_low = float(current_candle.lowPrice)

        # Calculate candle body and shadows
        body_size = abs(current_close - current_open)
        upper_shadow = current_high - max(current_open, current_close)
        lower_shadow = min(current_open, current_close) - current_low
        total_range = current_high - current_low

        if total_range == 0:
            return False

        body_ratio = body_size / total_range

        logger.info(f"5-minute candle analysis - body ratio: {body_ratio:.2f}, volume surge: {'yes' if volume_surge else 'no'}")

        if is_bullish:
            # Long signals: Reversal signals (hammer, morning star, etc.)
            is_hammer = (lower_shadow > body_size * 1.5 and upper_shadow < body_size * 0.5
                        and current_close > current_open)  # Hammer
            is_bullish_engulfing = False

            if prev_candle:
                prev_open = float(prev_candle.openPrice)
                prev_close = float(prev_candle.closePrice)
                # Bullish engulfing: previous bearish, current bullish, and current completely engulfs previous
                is_bullish_engulfing = (prev_close < prev_open and
                                      current_close > current_open and
                                      current_open < prev_close and
                                      current_close > prev_open)

            # Breakout condition: Break above previous 5-minute high
            breakthrough = False
            if prev_candle:
                prev_high = float(prev_candle.highPrice)
                breakthrough = current_high > prev_high

            signal_condition = (is_hammer or is_bullish_engulfing or breakthrough) and volume_surge

            logger.info(f"Long signals - hammer: {is_hammer}, engulfing: {is_bullish_engulfing}, breakout: {breakthrough}")
        else:
            # Short signals: Reversal signals
            is_shooting_star = (upper_shadow > body_size * 1.5 and lower_shadow < body_size * 0.5
                              and current_close < current_open)  # Shooting star
            is_bearish_engulfing = False

            if prev_candle:
                prev_open = float(prev_candle.openPrice)
                prev_close = float(prev_candle.closePrice)
                # Bearish engulfing: previous bullish, current bearish, and current completely engulfs previous
                is_bearish_engulfing = (prev_close > prev_open and
                                      current_close < current_open and
                                      current_open > prev_close and
                                      current_close < prev_open)

            # Breakdown condition: Break below previous 5-minute low
            breakdown = False
            if prev_candle:
                prev_low = float(prev_candle.lowPrice)
                breakdown = current_low < prev_low

            signal_condition = (is_shooting_star or is_bearish_engulfing or breakdown) and volume_surge

            logger.info(f"Short signals - shooting star: {is_shooting_star}, engulfing: {is_bearish_engulfing}, breakdown: {breakdown}")

        return signal_condition

    def _calculate_intraday_stop_loss(self, data_30m: List[KlineDataDTO], entry_price: float, is_bullish: bool) -> float:
        """Calculate intraday stop loss price"""
        # Extract 30-minute data
        closes = [float(item.closePrice) for item in data_30m]
        highs = [float(item.highPrice) for item in data_30m]
        lows = [float(item.lowPrice) for item in data_30m]

        if is_bullish:
            # Long stop loss: 30-minute previous low below 0.1%, or fixed stop loss 0.3%-0.5%
            recent_low = min(lows[-10:])  # Last 10 30-minute lows
            technical_stop = recent_low * 0.999
            fixed_stop = entry_price * 0.995  # 0.5% fixed stop loss

            stop_loss = max(technical_stop, fixed_stop)  # Take higher value, reduce risk
        else:
            # Short stop loss: 30-minute previous high above 0.1%, or fixed stop loss 0.3%-0.5%
            recent_high = max(highs[-10:])  # Last 10 30-minute highs
            technical_stop = recent_high * 1.001
            fixed_stop = entry_price * 1.005  # 0.5% fixed stop loss

            stop_loss = min(technical_stop, fixed_stop)  # Take lower value, reduce risk

        logger.info(f"Intraday stop loss price: {stop_loss:.6f}")
        return stop_loss

    def _calculate_intraday_take_profit(self, entry_price: float, stop_loss: float, is_bullish: bool) -> float:
        """Calculate intraday take profit price (1.5:1 risk/reward ratio)"""
        if is_bullish:
            risk = entry_price - stop_loss
            take_profit = entry_price + (risk * 1.5)  # 1.5:1 reward/risk ratio
        else:
            risk = stop_loss - entry_price
            take_profit = entry_price - (risk * 1.5)  # 1.5:1 reward/risk ratio

        logger.info(f"Intraday take profit price: {take_profit:.6f}")
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
                            k_period: int = 14, d_period: int = 3) -> List[float]:
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
