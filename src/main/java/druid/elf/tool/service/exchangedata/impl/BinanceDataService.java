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


    @Override
    protected String buildTradingPairsUrl() {
        return "https://api.binance.com/api/v3/exchangeInfo";
    }


    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode symbolsNode = rootNode.get("symbols");
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode symbolNode : symbolsNode) {
            String symbol = symbolNode.get("symbol").asText();
            String baseCurrency = symbolNode.get("baseAsset").asText();
            String quoteCurrency = symbolNode.get("quoteAsset").asText();
            String status = symbolNode.get("status").asText().equals("TRADING") ? "ACTIVE" : "INACTIVE";

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.BINANCE.name())
                    .setStatus(status)
                    .setInstrumentType("SPOT");


            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}