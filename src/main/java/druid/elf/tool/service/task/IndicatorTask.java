package druid.elf.tool.service.task;

import druid.elf.tool.entity.Settings;
import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.entity.TradeSignal;
import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.enums.ExchangeType;
import druid.elf.tool.enums.KlineInterval;
import druid.elf.tool.repository.TradeSignalRepository;
import druid.elf.tool.repository.TradingPairRepository;
import druid.elf.tool.service.DataService;
import druid.elf.tool.service.TradeSignalService;
import druid.elf.tool.service.TradeStrategyService;
import druid.elf.tool.service.exchangedata.ExchangeDataService;
import druid.elf.tool.service.proxy.ProxyPoolManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Component
public class IndicatorTask implements SchedulingConfigurer {

    @Autowired
    private DataService dataService;
    @Autowired
    private TradeSignalService tradeSignalService;
    @Autowired
    private TradeStrategyService tradeStrategyService;
    @Autowired
    private TradingPairRepository tradingPairRepository;
    @Autowired
    private SignalStorageService signalStorageService;
    @Autowired
    private TaskScheduler indicatorTaskScheduler;
    @Autowired
    private TradingPairTask.SyncExecutor tradingPairSyncExecutor;
    @Autowired
    private TradingPairTask.TradingPairSyncService tradingPairSyncService;


    private final ExecutorService executorService = new ThreadPoolExecutor(
            ExchangeType.values().length,
            ExchangeType.values().length * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PostConstruct
    public void init() {
        CompletableFuture<Void> tradingPairFuture = tradingPairSyncExecutor.executeSyncAsync();
        tradingPairFuture.thenRunAsync(this::generateAndStoreSignalAsync, executorService)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("IndicatorTask initial task failed", exception);
                    } else {
                        log.info("IndicatorTask initial task complete");
                    }
                });
        log.info("Triggered IndicatorTask async initialization, waiting for TradingPairTask to complete, project startup continues");
    }

    @Async
    public void generateAndStoreSignalAsync() {
        log.info("TradingPairTask completed, executing IndicatorTask trading signal generation");
        generateAndStoreSignal();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(indicatorTaskScheduler);
        taskRegistrar.addTriggerTask(
                this::generateAndStoreSignal,
                triggerContext -> {
                    Settings settings = tradeSignalService.getSettings();
                    Integer frequency = Optional.ofNullable(settings)
                            .map(Settings::getFetchFrequency)
                            .filter(f -> f > 0)
                            .orElse(15);
                    long interval = frequency * 60 * 1000L;
                    Instant lastCompletion = triggerContext.lastCompletion();
                    Instant nextExecutionTime = lastCompletion != null
                            ? lastCompletion.plusMillis(interval)
                            : Instant.now().plusMillis(interval);
                    return nextExecutionTime;
                }
        );
    }

    private void generateAndStoreSignal() {
        try {
            Settings settings = tradeSignalService.getSettings();
            Integer frequency = Optional.ofNullable(settings)
                    .map(Settings::getFetchFrequency)
                    .filter(f -> f > 0)
                    .orElse(15);
            log.info("Starting trading signal generation, current frequency: {} minutes", frequency);

            List<CompletableFuture<Void>> futures = Arrays.stream(ExchangeType.values())
                    .map(exchangeType -> CompletableFuture.runAsync(
                            () -> processExchange(exchangeType,settings), executorService))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Trading signal generation task completed");
        } catch (Exception e) {
            log.error("Trading signal generation task failed", e);
        }
    }


    private void processExchange(ExchangeType exchangeType, Settings settings) {

        if (settings == null || !settings.getExchangeTypes().contains(exchangeType.name())) {
            log.info("Exchange {} not in settings, skipping", exchangeType);
            return;
        }


        List<TradingPair> pairs = tradingPairRepository.findByExchange(exchangeType.name());
        if (pairs == null || pairs.isEmpty()) {
            log.warn("Not found {} trading pairs, please check data initialization", exchangeType);
            log.info("Re-fetching and initializing {} trading pairs", exchangeType);
            pairs = tradingPairSyncService.syncTradingPairs(settings);
            if (pairs == null || pairs.isEmpty()) {
                log.warn("Still not found {} trading pairs after re-fetch, ending K-line data fetch", exchangeType);
                return;
            }
        }
        log.info("Loaded {} with {} trading pairs", exchangeType, pairs.size());


        ProxyPoolManager proxyPool = new ProxyPoolManager(settings.getProxies());
        try {

            List<KlineInterval> intervals = Arrays.stream(KlineInterval.values())
                    .filter(i -> i != KlineInterval._1M && i != KlineInterval._5M)
                    .toList();


            List<CompletableFuture<Void>> futures = pairs.stream()
                    .map(pair -> proxyPool.submitTaskWithFuture(() -> {
                        String symbol = pair.getSymbol();
                        SettingsProxy proxy = proxyPool.getCurrentProxy();
                        ExchangeDataService service = dataService.createExchangeDataService(exchangeType, proxy);
                        Map<String, BarSeries> series = new HashMap<>();

                        for (KlineInterval interval : intervals) {
                            try {
                                series.put(interval.name(), service.getKlineData(symbol, interval, 300));
                            } catch (Exception e) {
                                log.error("Failed to get K-line data, exchange: {}, symbol: {}, interval: {}, proxy: {}:{}, error: {}",
                                        exchangeType, symbol, interval,
                                        proxy != null ? proxy.getIp() : "N/A",
                                        proxy != null ? proxy.getPort() : "N/A", e.getMessage());
                                break;
                            }
                        }

                        List<TradeSignal> signals = tradeStrategyService.generateSignal(series, symbol);
                        signals.forEach(s -> s.setExchange(exchangeType.name()));

                        if (!signals.isEmpty()) {

                            String priceInfo = (signals.get(0) != null && signals.get(0).getPrice() != null)
                                    ? signals.get(0).getPrice().toString()
                                    : "no price data";
                            log.info("Saving {} signals, exchange: {}, pair: {}, first signal price: {}",
                                    signals.size(),
                                    exchangeType,
                                    symbol,
                                    priceInfo);
                            signalStorageService.saveSignals(signals, exchangeType, symbol);
                        } else {

                            log.info("No signals to save, exchange: {}, pair: {}",
                                    exchangeType,
                                    symbol);
                        }
                    }))
                    .toList();


            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Completed all trading pair processing for {}", exchangeType);
        } catch (Exception e) {
            log.error("Processing exchange {} failed: {}", exchangeType, e.getMessage());
        } finally {
            proxyPool.shutdown();
            log.info("Proxy pool shutdown for {}", exchangeType);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down IndicatorTask thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                log.warn("Thread pool failed to shutdown within 60s, forced shutdown");
            } else {
                log.info("Thread pool shutdown successfully");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("Thread pool shutdown interrupted", e);
        }
    }
}

@Service
@Slf4j
class SignalStorageService {

    @Autowired
    private TradeSignalRepository tradeSignalRepository;

    @Transactional
    public void saveSignals(List<TradeSignal> signals, ExchangeType exchangeType, String symbol) {
        try {
            tradeSignalRepository.saveAll(signals);
            log.info("Successfully saved {} trading signals, exchange: {}, symbol: {}",
                    signals.size(), exchangeType, symbol);
        } catch (Exception e) {
            log.error("Failed to save trading signals, exchange: {}, symbol: {}", exchangeType, symbol, e);
            throw e;
        }
    }
}