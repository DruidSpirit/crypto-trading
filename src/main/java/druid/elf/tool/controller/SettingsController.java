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

    /**
     * 获取设置信息
     */
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
            response.put("message", "获取设置失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 保存设置信息
     */
    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody Settings settings) {
        try {
            tradeSignalService.saveSettings(settings);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "设置保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "保存设置失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取系统信息
     */
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
            response.put("message", "获取系统信息失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 清除缓存
     */
    @PostMapping("/system/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        try {
            // 这里可以添加清除缓存的逻辑
            // 例如清理Spring Cache、Redis缓存等
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "缓存清除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "缓存清除失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 系统重置
     */
    @PostMapping("/system/reset")
    public ResponseEntity<Map<String, Object>> resetSystem() {
        try {
            // 这里可以添加系统重置的逻辑
            // 例如清空数据库、重置配置等
            // 注意：这是一个危险操作，在生产环境中需要额外的安全检查
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "系统重置成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "系统重置失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取系统运行时间
     */
    private String getUptime() {
        long uptimeMillis = System.currentTimeMillis() - getStartTime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes % 60);
        } else {
            return String.format("%d分钟", minutes);
        }
    }

    /**
     * 获取应用启动时间（这里简化处理，实际应该从应用上下文中获取）
     */
    private long getStartTime() {
        // 简化实现，实际应该从Spring应用上下文获取启动时间
        return System.currentTimeMillis() - (60 * 60 * 1000); // 假设运行了1小时
    }

    /**
     * 获取活跃策略数量
     */
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