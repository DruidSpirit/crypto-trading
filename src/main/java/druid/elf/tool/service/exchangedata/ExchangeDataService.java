package druid.elf.tool.service.exchangedata;

import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.enums.KlineInterval;
import org.ta4j.core.BarSeries;
import java.io.IOException;
import java.util.List;

import druid.elf.tool.enums.ExchangeType;


public interface ExchangeDataService {

    BarSeries getKlineData(String symbol, KlineInterval interval, int dataCount) throws IOException;


    List<TradingPair> getTradingPairs() throws IOException;


    ExchangeType getExchangeType();
}
