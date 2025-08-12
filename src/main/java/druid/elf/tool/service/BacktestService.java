package druid.elf.tool.service;

import druid.elf.tool.dto.BacktestRequestDTO;
import druid.elf.tool.dto.BacktestResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class BacktestService {

    private final WebClient webClient;
    
    @Value("${python.strategy.service.url:http://localhost:8000}")
    private String pythonServiceUrl;

    public BacktestService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    public BacktestResponseDTO runBacktest(BacktestRequestDTO request) {
        try {
            log.info("开始回测请求：{}", request);
            
            BacktestResponseDTO response = webClient
                    .post()
                    .uri(pythonServiceUrl + "/api/backtest/run")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BacktestResponseDTO.class)
                    .timeout(Duration.ofMinutes(5)) // 回测可能需要较长时间
                    .block();
                    
            log.info("回测响应：成功={}", response != null ? response.isSuccess() : "null");
            return response;
            
        } catch (WebClientResponseException e) {
            log.error("回测请求失败，HTTP状态码：{}，响应体：{}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            
            BacktestResponseDTO errorResponse = new BacktestResponseDTO();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("回测请求失败: " + e.getResponseBodyAsString());
            return errorResponse;
            
        } catch (Exception e) {
            log.error("回测请求失败", e);
            
            BacktestResponseDTO errorResponse = new BacktestResponseDTO();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("回测请求失败: " + e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> downloadData(String symbol, String startDate, String endDate, String timeframe) {
        try {
            log.info("下载数据请求：symbol={}, startDate={}, endDate={}, timeframe={}", 
                symbol, startDate, endDate, timeframe);
            
            Map<String, Object> request = Map.of(
                "symbol", symbol,
                "start_date", startDate,
                "end_date", endDate,
                "timeframe", timeframe
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient
                    .post()
                    .uri(pythonServiceUrl + "/api/backtest/download-data")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(10)) // 数据下载可能需要较长时间
                    .block();
                    
            log.info("数据下载响应：{}", response);
            return response;
            
        } catch (Exception e) {
            log.error("数据下载失败", e);
            return Map.of(
                "success", false,
                "message", "数据下载失败: " + e.getMessage()
            );
        }
    }

    public Map<String, Object> batchDownloadData(String[] symbols, String startDate, String endDate, String timeframe) {
        try {
            log.info("批量下载数据请求：symbols={}, startDate={}, endDate={}, timeframe={}", 
                java.util.Arrays.toString(symbols), startDate, endDate, timeframe);
            
            Map<String, Object> request = Map.of(
                "symbols", symbols,
                "start_date", startDate,
                "end_date", endDate,
                "timeframe", timeframe
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient
                    .post()
                    .uri(pythonServiceUrl + "/api/backtest/batch-download")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(20)) // 批量下载需要更长时间
                    .block();
                    
            log.info("批量数据下载响应：{}", response);
            return response;
            
        } catch (Exception e) {
            log.error("批量数据下载失败", e);
            return Map.of(
                "success", false,
                "message", "批量数据下载失败: " + e.getMessage()
            );
        }
    }

    public Map<String, Object> getDataInfo() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient
                    .get()
                    .uri(pythonServiceUrl + "/api/backtest/data-info")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
                    
            log.info("获取数据信息成功");
            return response;
            
        } catch (Exception e) {
            log.error("获取数据信息失败", e);
            return Map.of(
                "success", false,
                "message", "获取数据信息失败: " + e.getMessage()
            );
        }
    }
}