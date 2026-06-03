package druid.elf.tool.service.client;

import druid.elf.tool.dto.PythonTradeSignalDTO;
import druid.elf.tool.dto.StrategyRequestDTO;
import druid.elf.tool.dto.StrategyResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class PythonStrategyClient {

    private final WebClient webClient;
    
    @Value("${python.strategy.service.url:http://localhost:8001}")
    private String pythonServiceUrl;

    public PythonStrategyClient() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public PythonTradeSignalDTO executeStrategy(StrategyRequestDTO request) {
        try {
            log.info("Calling Python strategy service, request params: {}", request);
            
            StrategyResponseDTO response = webClient
                    .post()
                    .uri(pythonServiceUrl + "/api/strategy/execute")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(StrategyResponseDTO.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
                    
            log.info("Python strategy service response: {}", response);
            
            if (response != null && response.isSuccess()) {
                return response.getData();
            } else {
                log.info("Python strategy service did not generate signal or execution failed: {}",
                    response != null ? response.getMessage() : "Response is null");
                return null;
            }
            
        } catch (WebClientResponseException e) {
            log.error("Failed to call Python strategy service, HTTP status code: {}, response body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Python strategy service", e);
            throw new RuntimeException("Failed to call Python strategy service: " + e.getMessage(), e);
        }
    }

    public boolean isHealthy() {
        try {
            String response = webClient
                    .get()
                    .uri(pythonServiceUrl + "/api/strategy/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return response != null && response.contains("healthy");
        } catch (Exception e) {
            log.warn("Python strategy service health check failed", e);
            return false;
        }
    }
    
    public String[] getAvailableStrategies() {
        try {
            log.info("Getting available strategy list");
            
            String response = webClient
                    .get()
                    .uri(pythonServiceUrl + "/api/strategy/strategies")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
                    
            log.info("Strategy list response: {}", response);

            return new String[]{"ElderSwingStrategy", "ElderIntradayStrategy"};
            
        } catch (Exception e) {
            log.warn("Failed to get strategy list", e);
            return new String[0];
        }
    }
}