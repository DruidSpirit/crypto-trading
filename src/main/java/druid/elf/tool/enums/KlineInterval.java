package druid.elf.tool.enums;

import druid.elf.tool.enums.exchange.BinanceKlineInterval;
import druid.elf.tool.enums.exchange.BybitKlineInterval;
import druid.elf.tool.enums.exchange.GateIoKlineInterval;
import druid.elf.tool.enums.exchange.OkxKlineInterval;


public enum KlineInterval {
    _1M,   // 1 minute
    _5M,   // 5 minutes
    _15M,  // 15 minutes
    _30M,  // 30 minutes
    _1H,   // 1 hour
    _4H,   // 4 hours
    _1D,   // 1 day
    _1W;   // 1 week


    public String getInterval(ExchangeType exchangeType) {
        if ( exchangeType == null ) return OkxKlineInterval.valueOf(this.name()).getSymbol();
        return switch (exchangeType) {
            case OKX -> OkxKlineInterval.valueOf(this.name()).getSymbol();
            case BINANCE -> BinanceKlineInterval.valueOf(this.name()).getSymbol();
            case GATE_IO -> GateIoKlineInterval.valueOf(this.name()).getSymbol();
            case BYBIT -> BybitKlineInterval.valueOf(this.name()).getSymbol();
        };
    }
}
