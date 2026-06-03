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


    @Override
    protected String buildTradingPairsUrl() {
        return "https://api.gateio.ws/api/v4/spot/currency_pairs";
    }


    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode dataNode = objectMapper.readTree(responseBody);
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode pairNode : dataNode) {
            String symbol = pairNode.get("id").asText();
            String baseCurrency = pairNode.get("base").asText();
            String quoteCurrency = pairNode.get("quote").asText();
            String status = pairNode.get("trade_status").asText().equals("tradable") ? "ACTIVE" : "INACTIVE";

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.GATE_IO.name())
                    .setStatus(status)
                    .setInstrumentType("SPOT");


            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}