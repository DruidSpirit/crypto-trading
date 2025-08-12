package druid.elf.tool.controller;

import druid.elf.tool.dto.BacktestRequestDTO;
import druid.elf.tool.dto.BacktestResponseDTO;
import druid.elf.tool.service.BacktestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/api/backtest")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    /**
     * 回测页面
     */
    @GetMapping("/page")
    public String backtestPage() {
        return "backtest";
    }

    /**
     * 运行回测
     */
    @ResponseBody
    @PostMapping("/run")
    public ResponseEntity<BacktestResponseDTO> runBacktest(@RequestBody BacktestRequestDTO request) {
        BacktestResponseDTO response = backtestService.runBacktest(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 下载单个交易对的历史数据
     */
    @ResponseBody
    @PostMapping("/download-data")
    public ResponseEntity<Map<String, Object>> downloadData(@RequestBody Map<String, String> request) {
        String symbol = request.get("symbol");
        String startDate = request.get("startDate");
        String endDate = request.get("endDate");
        String timeframe = request.getOrDefault("timeframe", "1h");
        
        Map<String, Object> response = backtestService.downloadData(symbol, startDate, endDate, timeframe);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量下载历史数据
     */
    @ResponseBody
    @PostMapping("/batch-download")
    public ResponseEntity<Map<String, Object>> batchDownloadData(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<String> symbolsList = (java.util.List<String>) request.get("symbols");
        String[] symbols = symbolsList.toArray(new String[0]);
        String startDate = (String) request.get("startDate");
        String endDate = (String) request.get("endDate");
        String timeframe = (String) request.getOrDefault("timeframe", "1h");
        
        Map<String, Object> response = backtestService.batchDownloadData(symbols, startDate, endDate, timeframe);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取本地数据信息
     */
    @ResponseBody
    @GetMapping("/data-info")
    public ResponseEntity<Map<String, Object>> getDataInfo() {
        Map<String, Object> response = backtestService.getDataInfo();
        return ResponseEntity.ok(response);
    }
}