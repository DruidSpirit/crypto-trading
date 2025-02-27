package druid.elf.tool.service.exchangedata;

import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.enums.KlineInterval;
import org.ta4j.core.BarSeries;
import java.io.IOException;
import java.util.List;

import druid.elf.tool.enums.ExchangeType;

/**
 * 交易所数据服务接口，定义统一的K线数据获取方法和交易所类型获取方法
 */
public interface ExchangeDataService {
    /**
     * 获取K线数据并解析为 BarSeries
     *
     * @param symbol    交易对，例如 "BTC-USDT"
     * @param interval  K线时间周期，例如 "1m"（1分钟）、"1h"（1小时）
     * @param dataCount 返回的数据条数
     * @return BarSeries 对象，包含K线数据的序列
     * @throws IOException 如果网络请求或解析失败，则抛出异常
     */
    BarSeries getKlineData(String symbol, KlineInterval interval, int dataCount) throws IOException;

    /**
     * 获取交易所的交易对信息
     */
    List<TradingPair> getTradingPairs() throws IOException;

    /**
     * 获取交易所类型
     *
     * @return ExchangeType 枚举值，表示当前服务的交易所类型
     */
    ExchangeType getExchangeType();
}
