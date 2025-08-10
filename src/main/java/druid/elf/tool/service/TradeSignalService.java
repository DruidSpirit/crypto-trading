package druid.elf.tool.service;

import cn.hutool.core.collection.CollectionUtil;
import druid.elf.tool.dto.SignalFilterDTO;
import druid.elf.tool.entity.Settings;
import druid.elf.tool.entity.TradeSignal;
import druid.elf.tool.enums.ExchangeType;
import druid.elf.tool.enums.TopCryptoCoin;
import druid.elf.tool.repository.SettingsRepository;
import druid.elf.tool.repository.TradeSignalRepository;
import druid.elf.tool.service.task.TradingPairTask;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.criteria.Expression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TradeSignalService {

    @Autowired
    private TradeSignalRepository tradeSignalRepository;
    @Autowired
    private SettingsRepository settingsRepository;

    public Page<TradeSignal> getSignals(SignalFilterDTO filter) {
        // 定义前 30 名主流加密货币关键词
        List<String> top30Coins = TopCryptoCoin.getAllSymbols();

        Specification<TradeSignal> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 交易对模糊搜索
            if (StringUtils.hasText(filter.getSearch())) {
                predicates.add(cb.like(cb.upper(root.get("symbol")), "%" + filter.getSearch().toUpperCase() + "%"));
            }

            // 交易所过滤
            if (StringUtils.hasText(filter.getExchange())) {
                predicates.add(cb.equal(root.get("exchange"), filter.getExchange()));
            }

            // 信号类型过滤
            if (StringUtils.hasText(filter.getSignalType())) {
                predicates.add(cb.equal(root.get("signal"), filter.getSignalType()));
            }

            // 策略过滤
            if (StringUtils.hasText(filter.getStrategy())) {
                predicates.add(cb.equal(root.get("strategy"), filter.getStrategy()));
            }

            // 开始日期过滤
            if (StringUtils.hasText(filter.getStartDate())) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("signalTime"),
                        LocalDateTime.parse(filter.getStartDate() + "T00:00:00")));
            }

            // 结束日期过滤
            if (StringUtils.hasText(filter.getEndDate())) {
                predicates.add(cb.lessThanOrEqualTo(root.get("signalTime"),
                        LocalDateTime.parse(filter.getEndDate() + "T23:59:59")));
            }

            // 添加优先级排序逻辑：symbol 是否包含前 30 名加密货币
            Expression<Integer> priority = cb.<Integer>selectCase()
                    .when(cb.or(
                            top30Coins.stream()
                                    .map(coin -> cb.like(root.get("symbol"), "%" + coin + "%"))
                                    .toArray(Predicate[]::new)
                    ), 1) // 匹配前 30 币种，优先级为 1
                    .otherwise(0); // 不匹配，优先级为 0

            // 设置查询的排序
            query.orderBy(
                    cb.desc(priority),           // 优先按匹配前 30 名排序（降序）
                    cb.desc(root.get("signalTime")) // 次级按 signalTime 降序
            );

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 分页参数（不再需要 Sort，因为已在 Specification 中定义）
        Pageable pageRequest = PageRequest.of(
                filter.getPage() != null ? filter.getPage() - 1 : 0,
                filter.getSize() != null ? filter.getSize() : 3
        );

        return tradeSignalRepository.findAll(spec, pageRequest);
    }

    @Transactional(readOnly = true)
    public Settings getSettings() {
        Settings settings = settingsRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Settings defaultSettings = new Settings()
                            .setCryptoMode("custom") // 默认加密货币模式
                            .setFetchFrequency(15) // 默认抓取频率15分钟
                            .setProxies(new ArrayList<>()); // 初始化为空列表，不设置默认代理
                    return settingsRepository.save(defaultSettings);
                });

        // 触发延迟加载并设置默认值
        if (CollectionUtil.isEmpty(settings.getExchangeTypes())) {
            settings.setExchangeTypes(List.of(ExchangeType.GATE_IO.name()));
        }
        if (CollectionUtil.isEmpty(settings.getCryptoSymbols()) && "custom".equals(settings.getCryptoMode())) {
            settings.setCryptoSymbols(TopCryptoCoin.getAllSymbols());
        }
        // 不设置默认代理，若 proxies 未初始化则设为空列表
        if (settings.getProxies() == null || CollectionUtil.isEmpty(settings.getProxies()) ) {
            settings.setProxies(new ArrayList<>());
        }

        return settings;
    }

    @Autowired
    private TradingPairTask.TradingPairSyncService tradingPairSyncService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Transactional(rollbackFor = Exception.class)
    public void saveSettings(Settings settings) {
        settingsRepository.findAll().stream()
                .findFirst()
                .ifPresent(existing -> settings.setId(existing.getId()));
        settingsRepository.save(settings);
        executor.submit(() -> tradingPairSyncService.syncTradingPairs(settings));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 获取仪表板统计数据
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取总信号数
        long totalSignals = tradeSignalRepository.count();
        
        // 获取买入信号数
        long buySignals = tradeSignalRepository.countBySignal("BUY");
        
        // 获取卖出信号数
        long sellSignals = tradeSignalRepository.countBySignal("SELL");
        
        // 获取活跃交易对数量
        long activePairs = tradeSignalRepository.countDistinctSymbols();
        
        // 计算较昨日的变化百分比（模拟数据，实际应该从历史数据计算）
        stats.put("totalSignals", totalSignals);
        stats.put("buySignals", buySignals);
        stats.put("sellSignals", sellSignals);
        stats.put("activePairs", activePairs);
        
        // 模拟变化百分比
        stats.put("totalChange", "+12%");
        stats.put("buyChange", "+8%");
        stats.put("sellChange", "-3%");
        stats.put("pairsChange", "+5%");
        
        return stats;
    }

    /**
     * 获取信号趋势图表数据
     */
    public Map<String, Object> getSignalChartData() {
        Map<String, Object> chartData = new HashMap<>();
        
        // 获取最近6个月的数据
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        
        // 按月份统计信号数量
        List<Map<String, Object>> monthlyData = tradeSignalRepository.getMonthlySignalStats(sixMonthsAgo);
        
        List<String> labels = new ArrayList<>();
        List<Integer> buyData = new ArrayList<>();
        List<Integer> sellData = new ArrayList<>();
        
        // 生成最近6个月的标签和初始化数据
        Map<String, Integer> buyMonthlyMap = new LinkedHashMap<>();
        Map<String, Integer> sellMonthlyMap = new LinkedHashMap<>();
        
        for (int i = 5; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String monthKey = month.format(DateTimeFormatter.ofPattern("yyyy-M"));
            String monthLabel = month.format(DateTimeFormatter.ofPattern("M月"));
            labels.add(monthLabel);
            buyMonthlyMap.put(monthKey, 0);
            sellMonthlyMap.put(monthKey, 0);
        }
        
        // 处理实际数据
        for (Map<String, Object> data : monthlyData) {
            Integer year = (Integer) data.get("year");
            Integer month = (Integer) data.get("month");
            String signal = (String) data.get("signal");
            Long count = (Long) data.get("count");
            
            String monthKey = year + "-" + month;
            
            if ("BUY".equals(signal)) {
                buyMonthlyMap.put(monthKey, count.intValue());
            } else if ("SELL".equals(signal)) {
                sellMonthlyMap.put(monthKey, count.intValue());
            }
        }
        
        // 转换为列表
        buyData.addAll(buyMonthlyMap.values());
        sellData.addAll(sellMonthlyMap.values());
        
        Map<String, Object> datasets = new HashMap<>();
        datasets.put("labels", labels);
        datasets.put("buyData", buyData);
        datasets.put("sellData", sellData);
        
        chartData.put("chartData", datasets);
        
        return chartData;
    }

    /**
     * 获取最新信号列表（不受筛选影响）
     */
    public List<TradeSignal> getLatestSignals(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return tradeSignalRepository.findByOrderBySignalTimeDesc(pageable).getContent();
    }
}