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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OKX 交易所数据服务实现类，提供OKX的K线数据获取功能和交易对信息
 */
@Component
public class OkxDataService extends AbstractExchangeDataService {

    public OkxDataService() {
        this(null);
    }

    public OkxDataService(SettingsProxy proxySettings) {
        super(proxySettings);
    }

    @Override
    protected String buildUrl(String symbol, String interval, int dataCount) {
        return String.format("https://www.okx.com/api/v5/market/candles?instId=%s&bar=%s&limit=%s",
                symbol, interval, dataCount);
    }

    @Override
    protected BarSeries parseKlineData(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        if (!"0".equals(rootNode.get("code").asText())) {
            throw new RuntimeException("API返回错误: " + rootNode.get("msg").asText());
        }

        JsonNode dataNode = rootNode.get("data");
        BarSeries series = new BaseBarSeries();
        List<JsonNode> klineList = new ArrayList<>();
        for (JsonNode klineNode : dataNode) {
            klineList.add(klineNode);
        }
        Collections.reverse(klineList);

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
        return ExchangeType.OKX;
    }

    /**
     * 构建OKX交易对信息的API请求URL
     *
     * @return 返回OKX的仪器信息API URL，用于获取所有类型的交易对（现货、合约等）
     */
    @Override
    protected String buildTradingPairsUrl() {
        return "https://www.okx.com/api/v5/public/instruments?instType=SPOT";
    }

    /**
     * 解析OKX返回的交易对数据JSON，转换为TradingPair对象列表
     *
     * @param responseBody API返回的JSON字符串，包含交易对信息
     * @return 返回解析后的TradingPair列表
     * @throws IOException 如果JSON解析失败或数据格式错误，抛出异常
     */
    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody); // 解析JSON响应
        if (!"0".equals(rootNode.get("code").asText())) {
            throw new RuntimeException("API返回错误: " + rootNode.get("msg").asText());
        }

        JsonNode dataNode = rootNode.get("data"); // 获取交易对数据数组
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode instrumentNode : dataNode) {
            String symbol = instrumentNode.get("instId").asText(); // 交易对名称，例如 BTC-USDT 或 BTC-USDT-SWAP
            String baseCurrency = instrumentNode.get("baseCcy").asText(null); // 基础货币，可能为空（合约中无此字段）
            String quoteCurrency = instrumentNode.get("quoteCcy").asText(null); // 报价货币，可能为空（合约中无此字段）
            String instrumentType = instrumentNode.get("instType").asText(); // 交易类型：SPOT, SWAP, FUTURES, OPTION
            String status = instrumentNode.get("state").asText().equals("live") ? "ACTIVE" : "INACTIVE"; // 状态转换为ACTIVE/INACTIVE
            String listTimeStr = instrumentNode.get("listTime").asText(null); // 上线时间（毫秒时间戳，可能为空）
            LocalDateTime listingDate = (listTimeStr != null && !listTimeStr.isEmpty()) ?
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(listTimeStr)), ZoneId.systemDefault()) : null;

            // 对于合约，可能需要从 symbol 中解析 baseCurrency 和 quoteCurrency
            if (baseCurrency == null || quoteCurrency == null) {
                String[] parts = symbol.split("-");
                if (parts.length >= 2) {
                    baseCurrency = parts[0]; // 例如 BTC-USDT-SWAP -> BTC
                    quoteCurrency = parts[1]; // 例如 BTC-USDT-SWAP -> USDT（需进一步处理 SWAP 后缀）
                    if ("SWAP".equals(instrumentType) || "FUTURES".equals(instrumentType)) {
                        quoteCurrency = parts.length > 2 ? parts[1] : parts[1]; // 假设 quoteCurrency 正确解析
                    }
                }
            }

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.OKX.name()) // 设置交易所名称
                    .setStatus(status)
                    .setInstrumentType(instrumentType) // 设置实际的交易类型
                    .setListingDate(listingDate); // 设置上线时间（若有）

            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}