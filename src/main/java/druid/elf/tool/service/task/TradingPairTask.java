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
        log.info("已触发定时交易对异步同步");
    }

    @Component
    public static class SyncExecutor {

        @Autowired
        private TradingPairSyncService syncService;

        @Async
        public CompletableFuture<Void> executeSyncAsync() {
            log.info("开始异步同步所有交易所的交易对数据");
            return CompletableFuture.runAsync(() -> syncService.executeSync())
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("交易对异步同步任务失败", exception);
                        } else {
                            log.info("交易对异步同步任务完成");
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
                    // 如果不是设置里面配置好的交易所则不执行数据拉取
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
                    log.info("从 {} 获取到 {} 个交易对", exchangeType, tradingPairs.size());

                    List<TradingPair> processedTradingPairs;
                    // 根据cryptoMode决定是否应用过滤
                    if ("custom".equals(settings.getCryptoMode())) {
                        processedTradingPairs = uniqueTradingPairs.stream()
                                .filter(tp -> {
                                    String symbolUpper = tp.getSymbol().toUpperCase();

                                    // 找到匹配的前缀币种
                                    Optional<TopCryptoCoin> matchedCoin = Arrays.stream(TopCryptoCoin.values())
                                            .filter(coin -> symbolUpper.startsWith(coin.getSymbol().toUpperCase()))
                                            .findFirst();

                                    if (matchedCoin.isEmpty()) return false;
                                    TopCryptoCoin coin = matchedCoin.get();

                                    // 检查前缀是否在topSymbols中
                                    boolean symbolMatches = topSymbols.stream().anyMatch(symbolUpper::startsWith);
                                    if (!symbolMatches) return false;

                                    // 获取前缀后面的剩余部分
                                    String remaining = symbolUpper.substring(coin.getSymbol().length());

                                    // 如果剩余部分为空，直接检查后缀
                                    if (remaining.isEmpty()) {
                                        return coin.getTradedAgainst().stream()
                                                .map(String::toUpperCase)
                                                .anyMatch(symbolUpper::endsWith);
                                    }

                                    // 检查连接符（只允许_或-）
                                    if (!remaining.startsWith("_") && !remaining.startsWith("-")) {
                                        return false;
                                    }

                                    // 获取连接符后面的部分
                                    String suffix = remaining.substring(1);

                                    // 检查后缀是否精确匹配tradedAgainst中的币种
                                    return coin.getTradedAgainst().stream()
                                            .map(String::toUpperCase)
                                            .anyMatch(suffix::equals);
                                })
                                .toList();
                    } else {
                        // "all"模式，直接使用所有uniqueTradingPairs
                        processedTradingPairs = uniqueTradingPairs;
                    }

                    allTradingPairs.addAll(processedTradingPairs);
                } catch (IOException e) {
                    log.error("获取 {} 交易对失败", exchangeType, e);
                } catch (Exception e) {
                    log.error("处理 {} 时发生未知错误", exchangeType, e);
                }
            }
            return saveTradingPairs(allTradingPairs);
        }

        private List<TradingPair> saveTradingPairs(List<TradingPair> tradingPairs) {
            try {
                if (!tradingPairs.isEmpty()) {
                    tradingPairRepository.saveAll(tradingPairs);
                    log.info("成功保存 {} 个交易对到数据库", tradingPairs.size());
                    return tradingPairs;
                } else {
                    log.warn("没有获取到任何交易对数据");
                }
            } catch (Exception e) {
                log.error("批量保存交易对到数据库失败", e);
                throw e;
            }
            return null;
        }
    }
}