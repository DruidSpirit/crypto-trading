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

//    @PostConstruct
//    public void init() {
//        CompletableFuture<Void> tradingPairFuture = tradingPairSyncExecutor.executeSyncAsync();
//        tradingPairFuture.thenRunAsync(this::generateAndStoreSignalAsync, executorService)
//                .whenComplete((result, exception) -> {
//                    if (exception != null) {
//                        log.error("IndicatorTask 初始任务失败", exception);
//                    } else {
//                        log.info("IndicatorTask 初始任务完成");
//                    }
//                });
//        log.info("已触发 IndicatorTask 异步初始化，等待 TradingPairTask 完成后执行，项目启动流程继续");
//    }

    @Async
    public void generateAndStoreSignalAsync() {
        log.info("TradingPairTask 已完成，异步执行 IndicatorTask 的交易信号生成");
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
            log.info("开始生成交易信号，当前频率：{} 分钟", frequency);

            List<CompletableFuture<Void>> futures = Arrays.stream(ExchangeType.values())
                    .map(exchangeType -> CompletableFuture.runAsync(
                            () -> processExchange(exchangeType,settings), executorService))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("本次交易信号生成任务已完成");
        } catch (Exception e) {
            log.error("生成交易信号任务失败", e);
        }
    }

    /**
     * 处理交易所数据，使用局部代理池并在任务完成后关闭
     * @param exchangeType 交易所类型
     * @param settings     设置对象，包含交易所类型和代理列表
     */
    private void processExchange(ExchangeType exchangeType, Settings settings) {
        // 检查交易所类型是否有效
        if (settings == null || !settings.getExchangeTypes().contains(exchangeType.name())) {
            log.info("交易所 {} 不在设置中，跳过处理", exchangeType);
            return;
        }

        // 加载交易对数据
        List<TradingPair> pairs = tradingPairRepository.findByExchange(exchangeType.name());
        if (pairs == null || pairs.isEmpty()) {
            log.warn("未找到 {} 的交易对，请检查数据初始化", exchangeType);
            log.info("再次获取并初始化{} 的交易对", exchangeType);
            pairs = tradingPairSyncService.syncTradingPairs(settings);
            if (pairs == null || pairs.isEmpty()) {
                log.warn("再次获取后还是未找到 {} 的交易对，请检查数据初始化，结束K线数据获取运行。", exchangeType);
                return;
            }
        }
        log.info("加载 {} 的 {} 条交易对", exchangeType, pairs.size());

        // 创建代理池，无代理时单线程运行
        ProxyPoolManager proxyPool = new ProxyPoolManager(settings.getProxies());
        try {
            // 定义K线间隔，排除1分钟和5分钟
            List<KlineInterval> intervals = Arrays.stream(KlineInterval.values())
                    .filter(i -> i != KlineInterval._1M && i != KlineInterval._5M)
                    .toList();

            // 提交任务并获取CompletableFuture列表
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
                                log.error("获取K线数据失败, 交易所: {}, 符号: {}, 间隔: {}, 代理: {}:{}, 错误: {}",
                                        exchangeType, symbol, interval,
                                        proxy != null ? proxy.getIp() : "无",
                                        proxy != null ? proxy.getPort() : "无", e.getMessage());
                                break;
                            }
                        }

                        List<TradeSignal> signals = tradeStrategyService.generateSignal(series, symbol);
                        signals.forEach(s -> s.setExchange(exchangeType.name()));
                        // signals 是否为空都要打印日志
                        if (!signals.isEmpty()) {
                            // 不为空的情况
                            String priceInfo = (signals.get(0) != null && signals.get(0).getPrice() != null)
                                    ? signals.get(0).getPrice().toString()
                                    : "无价格数据";
                            log.info("正在保存 {} 个信号，交易所: {}，交易对: {}，首个信号价格: {}",
                                    signals.size(),
                                    exchangeType,
                                    symbol,
                                    priceInfo);
                            signalStorageService.saveSignals(signals, exchangeType, symbol);
                        } else {
                            // 为空的情况
                            log.info("没有信号需要保存，交易所: {}，交易对: {}",
                                    exchangeType,
                                    symbol);
                        }
                    }))
                    .toList();

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("完成 {} 的所有交易对处理", exchangeType);
        } catch (Exception e) {
            log.error("处理交易所 {} 失败: {}", exchangeType, e.getMessage());
        } finally {
            proxyPool.shutdown();
            log.info("代理池关闭 for {}", exchangeType);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 IndicatorTask 的线程池...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                log.warn("线程池未能在 60 秒内正常关闭，已强制关闭");
            } else {
                log.info("线程池已正常关闭");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("关闭线程池时被中断", e);
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
            log.info("成功保存 {} 条交易信号, exchange: {}, symbol: {}",
                    signals.size(), exchangeType, symbol);
        } catch (Exception e) {
            log.error("保存交易信号失败, exchange: {}, symbol: {}", exchangeType, symbol, e);
            throw e;
        }
    }
}