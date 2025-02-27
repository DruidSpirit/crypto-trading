package druid.elf.tool.controller;

import druid.elf.tool.dto.SignalFilterDTO;
import druid.elf.tool.entity.Settings;
import druid.elf.tool.entity.TradeSignal;
import druid.elf.tool.enums.ExchangeType;
import druid.elf.tool.enums.TopCryptoCoin;
import druid.elf.tool.service.TradeSignalService;
import druid.elf.tool.service.strategy.TradeStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api")
public class TradeSignalController {

    @Autowired
    private TradeSignalService tradeSignalService;

    @Autowired
    private ApplicationContext applicationContext;

    // 页面跳转到 IP 信息列表页面
    @GetMapping("/index")
    public String index() {
        return "index";
    }

    /**
     * 获取信号列表（支持分页和过滤）
     */
    @ResponseBody
    @PostMapping("/signals/list")
    public ResponseEntity<Page<TradeSignal>> getSignals(@RequestBody SignalFilterDTO filter) {
        Page<TradeSignal> signals = tradeSignalService.getSignals(filter);
        return ResponseEntity.ok(signals);
    }

    /**
     * 获取设置
     */
    @ResponseBody
    @GetMapping("/getSettings")
    public ResponseEntity<Settings> getSettings() {
        Settings settings = tradeSignalService.getSettings();
        return ResponseEntity.ok(settings);
    }

    /**
     * 保存设置
     */
    @ResponseBody
    @PostMapping("/saveSettings")
    public ResponseEntity<Void> saveSettings(@RequestBody Settings settings) {
        tradeSignalService.saveSettings(settings);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取选择栏数据（交易所类型和交易策略名称）
     */
    @ResponseBody
    @GetMapping("/select-options")
    public ResponseEntity<Map<String, List<String>>> getSelectOptions() {
        // 获取所有的 ExchangeType 枚举名称
        List<String> exchangeTypes = Arrays.stream(ExchangeType.values())
                .map(Enum::name)
                .toList();

        // 通过 Spring 容器获取所有 TradeStrategy 类型的 bean
        Map<String, TradeStrategy> strategyBeans = applicationContext.getBeansOfType(TradeStrategy.class);
        List<String> strategyNames = strategyBeans.values().stream()
                .map(TradeStrategy::getStrategyName)
                .toList();

        // 构造返回的 JSON 数据
        Map<String, List<String>> response = Map.of(
                "exchangeTypes", exchangeTypes,
                "strategyNames", strategyNames,
                "defaultCryptoCoinSymbols", TopCryptoCoin.getAllSymbols()
        );

        return ResponseEntity.ok(response);
    }
}