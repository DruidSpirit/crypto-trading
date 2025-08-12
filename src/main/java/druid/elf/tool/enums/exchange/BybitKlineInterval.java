package druid.elf.tool.enums.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Bybit交易所的时间周期实现
 */
@Getter
@AllArgsConstructor
public enum BybitKlineInterval {
    _1M("1"),
    _5M("5"),
    _15M("15"),
    _30M("30"),
    _1H("60"),
    _4H("240"),
    _1D("D"),
    _1W("W");

    private final String symbol;

    public String getSymbol() {
        return symbol;
    }
}