package druid.elf.tool.enums.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OKX交易所的时间周期实现
 */
@Getter
@AllArgsConstructor
public enum OkxKlineInterval {
    _1M("1m"),
    _5M("5m"),
    _15M("15m"),
    _1H("1H"),
    _4H("4H"),
    _1D("1D"),
    _1W("1W");

    private final String symbol;

}
