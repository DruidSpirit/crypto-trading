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
    
    @Value("${python.strategy.service.url:http://localhost:8001}")
    private String pythonServiceUrl;

    public BacktestService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    public BacktestResponseDTO runBacktest(BacktestRequestDTO request) {
        try {
            log.info("Starting backtest request: {}", request);
            
            BacktestResponseDTO response = webClient
                    .post()
                    .uri(pythonServiceUrl + "/api/backtest/run")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BacktestResponseDTO.class)
                    .timeout(Duration.ofMinutes(5))
                    .block();
                    
            log.info("Backtest response: success={}", response != null ? response.isSuccess() : "null");
            return response;
            
        } catch (WebClientResponseException e) {
            log.error("Backtest request failed, HTTP status code: {}, response body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());

            BacktestResponseDTO errorResponse = new BacktestResponseDTO();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Backtest request failed: " + e.getResponseBodyAsString());
            return errorResponse;

        } catch (Exception e) {
            log.error("Backtest request failed", e);

            BacktestResponseDTO errorResponse = new BacktestResponseDTO();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Backtest request failed: " + e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> downloadData(String symbol, String startDate, String endDate, String timeframe) {
        try {
            log.info("Data download request: symbol={}, startDate={}, endDate={}, timeframe={}",
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
                    .timeout(Duration.ofMinutes(10))
                    .block();
                    
            log.info("Data download response: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("Data download failed", e);
            return Map.of(
                "success", false,
                "message", "Data download failed: " + e.getMessage()
            );
        }
    }

    public Map<String, Object> batchDownloadData(String[] symbols, String startDate, String endDate, String timeframe) {
        try {
            log.info("Batch data download request: symbols={}, startDate={}, endDate={}, timeframe={}",
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
                    .timeout(Duration.ofMinutes(20))
                    .block();
                    
            log.info("Batch data download response: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("Batch data download failed", e);
            return Map.of(
                "success", false,
                "message", "Batch data download failed: " + e.getMessage()
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
                    
            log.info("Data info retrieved successfully");
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get data information", e);
            return Map.of(
                "success", false,
                "message", "Failed to get data information: " + e.getMessage()
            );
        }
    }
}