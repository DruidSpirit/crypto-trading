package druid.elf.tool.enums.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Binance交易所的时间周期实现
 */
@Getter
@AllArgsConstructor
public enum BinanceKlineInterval {
    _1M("1m"),
    _5M("5m"),
    _15M("15m"),
    _1H("1h"),
    _4H("4h"),
    _1D("1d"),
    _1W("1w");

    private final String symbol;

}
