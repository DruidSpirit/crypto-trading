package druid.elf.tool.service.task;

import cn.hutool.core.collection.CollectionUtil;
import druid.elf.tool.entity.Settings;
import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.enums.ExchangeType;
import druid.elf.tool.enums.TopCryptoCoin;
import druid.elf.tool.repository.TradingPairRepository;
import druid.elf.tool.service.DataService;
import druid.elf.tool.service.TradeSignalService;
import druid.elf.tool.service.exchangedata.ExchangeDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TradingPairTask {

    @Autowired
    private SyncExecutor syncExecutor;

    @Scheduled(cron = "0 0 0 */3 * ?")
    public void syncTradingPairs() {
        syncExecutor.executeSyncAsync();
        log.info("Triggered scheduled trading pair async sync");
    }

    @Component
    public static class SyncExecutor {

        @Autowired
        private TradingPairSyncService syncService;

        @Async
        public CompletableFuture<Void> executeSyncAsync() {
            log.info("Starting async sync of trading pair data for all exchanges");
            return CompletableFuture.runAsync(() -> syncService.executeSync())
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("Trading pair async sync task failed", exception);
                        } else {
                            log.info("Trading pair async sync task completed");
                        }
                    });
        }
    }

    @Component
    @Slf4j
    public static class TradingPairSyncService {
        @Autowired
        private DataService dataService;
        @Autowired
        private TradingPairRepository tradingPairRepository;
        @Autowired
        private TradeSignalService tradeSignalService;

        @Transactional
        public void executeSync() {
            Settings settings = tradeSignalService.getSettings();
            syncTradingPairs(settings);
        }

        @Transactional
        public List<TradingPair> syncTradingPairs(Settings settings) {
            tradingPairRepository.deleteAll();
            List<TradingPair> allTradingPairs = new ArrayList<>();
            List<String> topSymbols = Optional.ofNullable(settings)
                    .map(Settings::getCryptoSymbols)
                    .filter(CollectionUtil::isNotEmpty)
                    .orElseGet(TopCryptoCoin::getAllSymbols)
                    .stream()
                    .map(String::toUpperCase)
                    .toList();

            if (settings == null) settings = new Settings();

            for (ExchangeType exchangeType : ExchangeType.values()) {
                try {

                    if (!settings.getExchangeTypes().contains(exchangeType.name())) continue;

                    SettingsProxy settingsProxy = CollectionUtil.isNotEmpty(settings.getProxies()) ? settings.getProxies().get(0) : null;
                    ExchangeDataService exchangeService = dataService.createExchangeDataService(exchangeType, settingsProxy);
                    List<TradingPair> tradingPairs = exchangeService.getTradingPairs();
                    List<TradingPair> uniqueTradingPairs = tradingPairs.stream()
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toMap(
                                            TradingPair::getSymbol,
                                            tp -> tp,
                                            (existing, replacement) -> existing
                                    ),
                                    map -> List.copyOf(map.values())
                            ));
                    log.info("From {} fetched {} trading pairs", exchangeType, tradingPairs.size());

                    List<TradingPair> processedTradingPairs;

                    if ("custom".equals(settings.getCryptoMode())) {
                        processedTradingPairs = uniqueTradingPairs.stream()
                                .filter(tp -> {
                                    String symbolUpper = tp.getSymbol().toUpperCase();


                                    Optional<TopCryptoCoin> matchedCoin = Arrays.stream(TopCryptoCoin.values())
                                            .filter(coin -> symbolUpper.startsWith(coin.getSymbol().toUpperCase()))
                                            .findFirst();

                                    if (matchedCoin.isEmpty()) return false;
                                    TopCryptoCoin coin = matchedCoin.get();


                                    boolean symbolMatches = topSymbols.stream().anyMatch(symbolUpper::startsWith);
                                    if (!symbolMatches) return false;


                                    String remaining = symbolUpper.substring(coin.getSymbol().length());


                                    if (remaining.isEmpty()) {
                                        return coin.getTradedAgainst().stream()
                                                .map(String::toUpperCase)
                                                .anyMatch(symbolUpper::endsWith);
                                    }


                                    if (!remaining.startsWith("_") && !remaining.startsWith("-")) {
                                        return false;
                                    }


                                    String suffix = remaining.substring(1);


                                    return coin.getTradedAgainst().stream()
                                            .map(String::toUpperCase)
                                            .anyMatch(suffix::equals);
                                })
                                .toList();
                    } else {

                        processedTradingPairs = uniqueTradingPairs;
                    }

                    allTradingPairs.addAll(processedTradingPairs);
                } catch (IOException e) {
                    log.error("Failed to get trading pairs for {}", exchangeType, e);
                } catch (Exception e) {
                    log.error("Unknown error processing {}", exchangeType, e);
                }
            }
            return saveTradingPairs(allTradingPairs);
        }

        private List<TradingPair> saveTradingPairs(List<TradingPair> tradingPairs) {
            try {
                if (!tradingPairs.isEmpty()) {
                    tradingPairRepository.saveAll(tradingPairs);
                    log.info("Successfully saved {} trading pairs to database", tradingPairs.size());
                    return tradingPairs;
                } else {
                    log.warn("No trading pair data retrieved");
                }
            } catch (Exception e) {
                log.error("Failed to batch save trading pairs to database", e);
                throw e;
            }
            return null;
        }
    }
}