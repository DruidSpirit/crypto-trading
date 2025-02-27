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
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate.io 交易所数据服务实现类，提供Gate.io的K线数据获取功能和交易对信息
 */
@Component
public class GateIoDataService extends AbstractExchangeDataService {

    public GateIoDataService() {
        this(null);
    }

    public GateIoDataService(SettingsProxy proxySettings) {
        super(proxySettings);
    }

    @Override
    protected String buildUrl(String symbol, String interval, int dataCount) {
        return String.format("https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=%s&interval=%s&limit=%s",
                symbol.replace("-", "_"), interval.toLowerCase(), dataCount);
    }

    @Override
    protected BarSeries parseKlineData(String responseBody) throws IOException {
        JsonNode dataNode = objectMapper.readTree(responseBody);
        BarSeries series = new BaseBarSeries();

        for (JsonNode klineNode : dataNode) {
            long timestamp = Long.parseLong(klineNode.get(0).asText()) * 1000;
            double volume = Double.parseDouble(klineNode.get(1).asText());
            double close = Double.parseDouble(klineNode.get(2).asText());
            double high = Double.parseDouble(klineNode.get(3).asText());
            double low = Double.parseDouble(klineNode.get(4).asText());
            double open = Double.parseDouble(klineNode.get(5).asText());

            series.addBar(buildBar(timestamp, open, high, low, close, volume));
        }
        return series;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATE_IO;
    }

    /**
     * 构建Gate.io交易对信息的API请求URL
     *
     * @return 返回Gate.io的货币对信息API URL，用于获取所有现货交易对
     */
    @Override
    protected String buildTradingPairsUrl() {
        return "https://api.gateio.ws/api/v4/spot/currency_pairs";
    }

    /**
     * 解析Gate.io返回的交易对数据JSON，转换为TradingPair对象列表
     *
     * @param responseBody API返回的JSON字符串，包含交易对信息
     * @return 返回解析后的TradingPair列表
     * @throws IOException 如果JSON解析失败或数据格式错误，抛出异常
     */
    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode dataNode = objectMapper.readTree(responseBody); // 解析JSON响应
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode pairNode : dataNode) {
            String symbol = pairNode.get("id").asText(); // 交易对名称，例如BTC_USDT
            String baseCurrency = pairNode.get("base").asText(); // 基础货币，例如BTC
            String quoteCurrency = pairNode.get("quote").asText(); // 报价货币，例如USDT
            String status = pairNode.get("trade_status").asText().equals("tradable") ? "ACTIVE" : "INACTIVE"; // 状态转换为ACTIVE/INACTIVE

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.GATE_IO.name()) // 设置交易所名称
                    .setStatus(status)
                    .setInstrumentType("SPOT"); // 默认设置为现货交易
            // 注意：Gate.io未提供listingDate，可通过其他API或公告补充

            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}