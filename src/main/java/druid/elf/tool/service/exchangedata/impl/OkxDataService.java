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
            throw new RuntimeException("API returned error: " + rootNode.get("msg").asText());
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


    @Override
    protected String buildTradingPairsUrl() {
        return "https://www.okx.com/api/v5/public/instruments?instType=SPOT";
    }


    @Override
    protected List<TradingPair> fetchTradingPairs(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        if (!"0".equals(rootNode.get("code").asText())) {
            throw new RuntimeException("API returned error: " + rootNode.get("msg").asText());
        }

        JsonNode dataNode = rootNode.get("data");
        List<TradingPair> tradingPairs = new ArrayList<>();

        for (JsonNode instrumentNode : dataNode) {
            String symbol = instrumentNode.get("instId").asText();
            String baseCurrency = instrumentNode.get("baseCcy").asText(null);
            String quoteCurrency = instrumentNode.get("quoteCcy").asText(null);
            String instrumentType = instrumentNode.get("instType").asText();
            String status = instrumentNode.get("state").asText().equals("live") ? "ACTIVE" : "INACTIVE";
            String listTimeStr = instrumentNode.get("listTime").asText(null);
            LocalDateTime listingDate = (listTimeStr != null && !listTimeStr.isEmpty()) ?
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(listTimeStr)), ZoneId.systemDefault()) : null;


            if (baseCurrency == null || quoteCurrency == null) {
                String[] parts = symbol.split("-");
                if (parts.length >= 2) {
                    baseCurrency = parts[0];
                    quoteCurrency = parts[1];
                    if ("SWAP".equals(instrumentType) || "FUTURES".equals(instrumentType)) {
                        quoteCurrency = parts.length > 2 ? parts[1] : parts[1];
                    }
                }
            }

            TradingPair tradingPair = new TradingPair()
                    .setSymbol(symbol)
                    .setBaseCurrency(baseCurrency)
                    .setQuoteCurrency(quoteCurrency)
                    .setExchange(ExchangeType.OKX.name())
                    .setStatus(status)
                    .setInstrumentType(instrumentType)
                    .setListingDate(listingDate);

            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }
}