package druid.elf.tool.controller;

import druid.elf.tool.dto.SignalFilterDTO;
import druid.elf.tool.entity.Settings;
import druid.elf.tool.entity.StrategyFile;
import druid.elf.tool.entity.TradeSignal;
import druid.elf.tool.enums.ExchangeType;
import druid.elf.tool.enums.TopCryptoCoin;
import druid.elf.tool.service.StrategyFileService;
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
    
    @Autowired
    private StrategyFileService strategyFileService;


    @GetMapping("/index")
    public String index() {
        return "index";
    }


    @ResponseBody
    @PostMapping("/signals/list")
    public ResponseEntity<Page<TradeSignal>> getSignals(@RequestBody SignalFilterDTO filter) {
        Page<TradeSignal> signals = tradeSignalService.getSignals(filter);
        return ResponseEntity.ok(signals);
    }


    @ResponseBody
    @GetMapping("/getSettings")
    public ResponseEntity<Settings> getSettings() {
        Settings settings = tradeSignalService.getSettings();
        return ResponseEntity.ok(settings);
    }


    @ResponseBody
    @PostMapping("/saveSettings")
    public ResponseEntity<Void> saveSettings(@RequestBody Settings settings) {
        tradeSignalService.saveSettings(settings);
        return ResponseEntity.ok().build();
    }


    @ResponseBody
    @GetMapping("/select-options")
    public ResponseEntity<Map<String, List<String>>> getSelectOptions() {

        List<String> exchangeTypes = Arrays.stream(ExchangeType.values())
                .map(Enum::name)
                .toList();



        List<String> strategyNames = strategyFileService.getAllStrategies().stream()
                .filter(strategy -> strategy.getStatus() == StrategyFile.StrategyStatus.ACTIVE)
                .map(strategy -> {

                    if (strategy.getDisplayName() != null && !strategy.getDisplayName().trim().isEmpty()) {
                        return strategy.getDisplayName();
                    } else if (strategy.getDescription() != null && !strategy.getDescription().trim().isEmpty()) {
                        return strategy.getDescription();
                    } else {
                        String filename = strategy.getOriginalFilename();
                        return filename.endsWith(".py") ? filename.substring(0, filename.length() - 3) : filename;
                    }
                })
                .distinct()
                .sorted()
                .toList();


        Map<String, List<String>> response = Map.of(
                "exchangeTypes", exchangeTypes,
                "strategyNames", strategyNames,
                "defaultCryptoCoinSymbols", TopCryptoCoin.getAllSymbols()
        );

        return ResponseEntity.ok(response);
    }


    @ResponseBody
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = tradeSignalService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }


    @ResponseBody
    @GetMapping("/dashboard/chart")
    public ResponseEntity<Map<String, Object>> getSignalChart() {
        Map<String, Object> chartData = tradeSignalService.getSignalChartData();
        return ResponseEntity.ok(chartData);
    }


    @ResponseBody
    @GetMapping("/dashboard/latest-signals")
    public ResponseEntity<List<TradeSignal>> getDashboardLatestSignals(@RequestParam(defaultValue = "5") int limit) {
        List<TradeSignal> latestSignals = tradeSignalService.getLatestSignals(limit);
        return ResponseEntity.ok(latestSignals);
    }


    @ResponseBody
    @GetMapping("/signals")
    public ResponseEntity<Page<TradeSignal>> getSignalsWithParams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String signalType,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        SignalFilterDTO filter = new SignalFilterDTO();
        filter.setPage(page + 1);  // Convert 0-based to 1-based page number
        filter.setSize(size);
        filter.setSearch(search);
        filter.setSignalType(signalType);
        filter.setStrategy(strategy);
        filter.setExchange(exchange);
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        
        Page<TradeSignal> signals = tradeSignalService.getSignals(filter);
        return ResponseEntity.ok(signals);
    }
}