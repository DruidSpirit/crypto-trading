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

        List<String> top30Coins = TopCryptoCoin.getAllSymbols();

        Specification<TradeSignal> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();


            if (StringUtils.hasText(filter.getSearch())) {
                predicates.add(cb.like(cb.upper(root.get("symbol")), "%" + filter.getSearch().toUpperCase() + "%"));
            }


            if (StringUtils.hasText(filter.getExchange())) {
                predicates.add(cb.equal(root.get("exchange"), filter.getExchange()));
            }


            if (StringUtils.hasText(filter.getSignalType())) {
                predicates.add(cb.equal(root.get("signal"), filter.getSignalType()));
            }


            if (StringUtils.hasText(filter.getStrategy())) {
                predicates.add(cb.equal(root.get("strategy"), filter.getStrategy()));
            }


            if (StringUtils.hasText(filter.getStartDate())) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("signalTime"),
                        LocalDateTime.parse(filter.getStartDate() + "T00:00:00")));
            }


            if (StringUtils.hasText(filter.getEndDate())) {
                predicates.add(cb.lessThanOrEqualTo(root.get("signalTime"),
                        LocalDateTime.parse(filter.getEndDate() + "T23:59:59")));
            }


            Expression<Integer> priority = cb.<Integer>selectCase()
                    .when(cb.or(
                            top30Coins.stream()
                                    .map(coin -> cb.like(root.get("symbol"), "%" + coin + "%"))
                                    .toArray(Predicate[]::new)
                    ), 1)
                    .otherwise(0);


            query.orderBy(
                    cb.desc(priority),
                    cb.desc(root.get("signalTime"))
            );

            return cb.and(predicates.toArray(new Predicate[0]));
        };


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
                            .setCryptoMode("custom")
                            .setFetchFrequency(15)
                            .setProxies(new ArrayList<>());
                    return settingsRepository.save(defaultSettings);
                });


        if (CollectionUtil.isEmpty(settings.getExchangeTypes())) {
            settings.setExchangeTypes(List.of(ExchangeType.GATE_IO.name()));
        }
        if (CollectionUtil.isEmpty(settings.getCryptoSymbols()) && "custom".equals(settings.getCryptoMode())) {
            settings.setCryptoSymbols(TopCryptoCoin.getAllSymbols());
        }

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


    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        

        long totalSignals = tradeSignalRepository.count();
        

        long buySignals = tradeSignalRepository.countBySignal("BUY");
        

        long sellSignals = tradeSignalRepository.countBySignal("SELL");
        

        long activePairs = tradeSignalRepository.countDistinctSymbols();
        

        stats.put("totalSignals", totalSignals);
        stats.put("buySignals", buySignals);
        stats.put("sellSignals", sellSignals);
        stats.put("activePairs", activePairs);
        

        stats.put("totalChange", "+12%");
        stats.put("buyChange", "+8%");
        stats.put("sellChange", "-3%");
        stats.put("pairsChange", "+5%");
        
        return stats;
    }


    public Map<String, Object> getSignalChartData() {
        Map<String, Object> chartData = new HashMap<>();
        

        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        

        List<Map<String, Object>> monthlyData = tradeSignalRepository.getMonthlySignalStats(sixMonthsAgo);
        
        List<String> labels = new ArrayList<>();
        List<Integer> buyData = new ArrayList<>();
        List<Integer> sellData = new ArrayList<>();
        

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
        

        buyData.addAll(buyMonthlyMap.values());
        sellData.addAll(sellMonthlyMap.values());
        
        Map<String, Object> datasets = new HashMap<>();
        datasets.put("labels", labels);
        datasets.put("buyData", buyData);
        datasets.put("sellData", sellData);
        
        chartData.put("chartData", datasets);
        
        return chartData;
    }


    public List<TradeSignal> getLatestSignals(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return tradeSignalRepository.findByOrderBySignalTimeDesc(pageable).getContent();
    }
}