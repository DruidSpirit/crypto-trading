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


        List<JsonNode> klineList = new ArrayList<>();
        dataNode.forEach(klineList::add);
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
        return ExchangeType.BYBIT;
    }


    @Override
    protected String buildTradingPairsUrl() {
        return "https://api.bybit.com/v5/market/instruments-info?category=spot";
    }


    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode listNode = rootNode.get("result").get("list");
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode instrumentNode : listNode) {
            String symbol = instrumentNode.get("symbol").asText();
            String baseCurrency = instrumentNode.get("baseCoin").asText();
            String quoteCurrency = instrumentNode.get("quoteCoin").asText();
            String status = instrumentNode.get("status").asText().equals("Trading") ? "ACTIVE" : "INACTIVE";

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.BYBIT.name())
                    .setStatus(status)
                    .setInstrumentType("SPOT");


            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}