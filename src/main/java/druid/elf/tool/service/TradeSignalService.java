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
import java.util.ArrayList;
import java.util.List;
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
}