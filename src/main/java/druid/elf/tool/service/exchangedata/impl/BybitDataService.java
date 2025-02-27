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
import java.util.Collections;
import java.util.List;

/**
 * Bybit 交易所数据服务实现类，提供Bybit的K线数据获取功能和交易对信息
 */
@Component
public class BybitDataService extends AbstractExchangeDataService {

    public BybitDataService() {
        this(null);
    }

    public BybitDataService(SettingsProxy proxySettings) {
        super(proxySettings);
    }

    @Override
    protected String buildUrl(String symbol, String interval, int dataCount) {
        String intervalNum = interval.replaceAll("[^0-9]", "");
        String unit = interval.replaceAll("[0-9]", "").toLowerCase();
        String bybitInterval = intervalNum.isEmpty() ? "1" : intervalNum;
        if ("h".equals(unit)) {
            bybitInterval = String.valueOf(Integer.parseInt(intervalNum) * 60);
        }
        return String.format("https://api.bybit.com/v5/market/kline?category=spot&symbol=%s&interval=%s&limit=%s",
                symbol.replace("-", ""), bybitInterval, dataCount);
    }

    @Override
    protected BarSeries parseKlineData(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode dataNode = rootNode.get("result").get("list");
        BarSeries series = new BaseBarSeries();

        // 将数据转换为列表并反转顺序
        List<JsonNode> klineList = new ArrayList<>();
        dataNode.forEach(klineList::add);
        Collections.reverse(klineList); // 反转顺序，从最旧到最新

        for (JsonNode klineNode : klineList) {
            long timestamp = Long.parseLong(klineNode.get(0).asText());
            double open = Double.parseDouble(klineNode.get(1).asText());
            double high = Double.parseDouble(klineNode.get(2).asText());
            double low = Double.parseDouble(klineNode.get(3).asText());
            double close = Double.parseDouble(klineNode.get(4).asText());
            double volume = Double.parseDouble(klineNode.get(5).asText());

            series.addBar(buildBar(timestamp, open, high, low, close, volume));
        }
        return series;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    /**
     * 构建Bybit交易对信息的API请求URL
     *
     * @return 返回Bybit的仪器信息API URL，用于获取所有现货交易对
     */
    @Override
    protected String buildTradingPairsUrl() {
        return "https://api.bybit.com/v5/market/instruments-info?category=spot";
    }

    /**
     * 解析Bybit返回的交易对数据JSON，转换为TradingPair对象列表
     *
     * @param responseBody API返回的JSON字符串，包含交易对信息
     * @return 返回解析后的TradingPair列表
     * @throws IOException 如果JSON解析失败或数据格式错误，抛出异常
     */
    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody); // 解析JSON响应
        JsonNode listNode = rootNode.get("result").get("list"); // 获取交易对列表
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode instrumentNode : listNode) {
            String symbol = instrumentNode.get("symbol").asText(); // 交易对名称，例如BTCUSDT
            String baseCurrency = instrumentNode.get("baseCoin").asText(); // 基础货币，例如BTC
            String quoteCurrency = instrumentNode.get("quoteCoin").asText(); // 报价货币，例如USDT
            String status = instrumentNode.get("status").asText().equals("Trading") ? "ACTIVE" : "INACTIVE"; // 状态转换为ACTIVE/INACTIVE

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.BYBIT.name()) // 设置交易所名称
                    .setStatus(status)
                    .setInstrumentType("SPOT"); // 默认设置为现货交易
            // 注意：Bybit未提供listingDate，可通过其他API或公告补充

            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}