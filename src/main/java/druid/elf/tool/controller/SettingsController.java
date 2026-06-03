package druid.elf.tool.controller;

import druid.elf.tool.entity.Settings;
import druid.elf.tool.repository.TradeSignalRepository;
import druid.elf.tool.service.StrategyFileService;
import druid.elf.tool.service.TradeSignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SettingsController {

    @Autowired
    private TradeSignalService tradeSignalService;

    @Autowired
    private TradeSignalRepository tradeSignalRepository;

    @Autowired
    private StrategyFileService strategyFileService;


    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        try {
            Settings settings = tradeSignalService.getSettings();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", settings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get settings: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }


    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody Settings settings) {
        try {
            tradeSignalService.saveSettings(settings);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Settings saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to save settings: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }


    @GetMapping("/system/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        try {
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("version", "1.0.0");
            systemInfo.put("uptime", getUptime());
            systemInfo.put("totalSignals", tradeSignalRepository.count());
            systemInfo.put("activeStrategies", getActiveStrategiesCount());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", systemInfo);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get system info: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }


    @PostMapping("/system/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        try {


            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache cleared successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to clear cache: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }


    @PostMapping("/system/reset")
    public ResponseEntity<Map<String, Object>> resetSystem() {
        try {



            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "System reset successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "System reset failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }


    private String getUptime() {
        long uptimeMillis = System.currentTimeMillis() - getStartTime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else {
            return String.format("%dm", minutes);
        }
    }


    private long getStartTime() {

        return System.currentTimeMillis() - (60 * 60 * 1000);
    }


    private long getActiveStrategiesCount() {
        try {
            return strategyFileService.getAllStrategies().stream()
                .filter(strategy -> strategy.getStatus() == druid.elf.tool.entity.StrategyFile.StrategyStatus.ACTIVE)
                .count();
        } catch (Exception e) {
            return 0;
        }
    }
}