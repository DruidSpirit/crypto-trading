package druid.elf.tool.service.exchangedata.impl;

import com.fasterxml.jackson.databind.JsonNode;
import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.enums.ExchangeType;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import druid.elf.tool.service.exchangedata.AbstractExchangeDataService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Binance 交易所数据服务实现类，提供Binance的K线数据获取功能和交易对信息
 */
@Component
public class BinanceDataService extends AbstractExchangeDataService {

    public BinanceDataService() {
        this(null);
    }

    public BinanceDataService(SettingsProxy proxySettings) {
        super(proxySettings);
    }

    @Override
    protected String buildUrl(String symbol, String interval, int dataCount) {
        return String.format("https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&limit=%s",
                symbol.replace("-", ""), interval.toLowerCase(), dataCount);
    }

    @Override
    protected BarSeries parseKlineData(String responseBody) throws IOException {
        JsonNode dataNode = objectMapper.readTree(responseBody);
        BarSeries series = new BaseBarSeries();

        for (JsonNode klineNode : dataNode) {
            long timestamp = klineNode.get(0).asLong();
            double open = klineNode.get(1).asDouble();
            double high = klineNode.get(2).asDouble();
            double low = klineNode.get(3).asDouble();
            double close = klineNode.get(4).asDouble();
            double volume = klineNode.get(5).asDouble();

            series.addBar(buildBar(timestamp, open, high, low, close, volume));
        }
        return series;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINANCE;
    }

    /**
     * 构建Binance交易对信息的API请求URL
     *
     * @return 返回Binance的交易所信息API URL，用于获取所有交易对
     */
    @Override
    protected String buildTradingPairsUrl() {
        return "https://api.binance.com/api/v3/exchangeInfo";
    }

    /**
     * 解析Binance返回的交易对数据JSON，转换为TradingPair对象列表
     *
     * @param responseBody API返回的JSON字符串，包含交易对信息
     * @return 返回解析后的TradingPair列表
     * @throws IOException 如果JSON解析失败或数据格式错误，抛出异常
     */
    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody); // 解析JSON响应
        JsonNode symbolsNode = rootNode.get("symbols"); // 获取交易对数组
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode symbolNode : symbolsNode) {
            String symbol = symbolNode.get("symbol").asText(); // 交易对名称，例如BTCUSDT
            String baseCurrency = symbolNode.get("baseAsset").asText(); // 基础货币，例如BTC
            String quoteCurrency = symbolNode.get("quoteAsset").asText(); // 报价货币，例如USDT
            String status = symbolNode.get("status").asText().equals("TRADING") ? "ACTIVE" : "INACTIVE"; // 状态转换为ACTIVE/INACTIVE

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.BINANCE.name()) // 设置交易所名称
                    .setStatus(status)
                    .setInstrumentType("SPOT"); // 默认设置为现货交易
            // 注意：Binance未提供listingDate，可通过其他API或公告补充

            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}