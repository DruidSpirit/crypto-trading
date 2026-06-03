package druid.elf.tool.service.client;

import druid.elf.tool.dto.PythonTradeSignalDTO;
import druid.elf.tool.dto.StrategyRequestDTO;
import druid.elf.tool.dto.StrategyResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;


@Service
@Slf4j
public class PythonStrategyClientRestTemplate {

    private final RestTemplate restTemplate;
    
    @Value("${python.strategy.service.url:http://localhost:8001}")
    private String pythonServiceUrl;

    public PythonStrategyClientRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PythonTradeSignalDTO executeStrategy(StrategyRequestDTO request) {
        try {
            log.info("Calling Python strategy service, request params: {}", request);
            

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            

            HttpEntity<StrategyRequestDTO> requestEntity = new HttpEntity<>(request, headers);
            

            String url = pythonServiceUrl + "/api/strategy/execute";
            ResponseEntity<StrategyResponseDTO> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                requestEntity, 
                StrategyResponseDTO.class
            );
            
            log.info("Python strategy service response status: {}", response.getStatusCode());
            log.info("Python strategy service response: {}", response.getBody());
            
            StrategyResponseDTO responseBody = response.getBody();
            if (responseBody != null && responseBody.isSuccess()) {
                return responseBody.getData();
            } else {
                log.info("Python strategy service did not generate signal or execution failed: {}",
                    responseBody != null ? responseBody.getMessage() : "Response is null");
                return null;
            }
            
        } catch (RestClientException e) {
            log.error("Failed to call Python strategy service", e);
            return null;
        } catch (Exception e) {
            log.error("Failed to call Python strategy service", e);
            throw new RuntimeException("Failed to call Python strategy service: " + e.getMessage(), e);
        }
    }

    public boolean isHealthy() {
        try {
            String url = pythonServiceUrl + "/api/strategy/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            String responseBody = response.getBody();
            return response.getStatusCode() == HttpStatus.OK && 
                   responseBody != null && responseBody.contains("healthy");
                   
        } catch (Exception e) {
            log.warn("Python strategy service health check failed", e);
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    public String[] getAvailableStrategies() {
        try {
            log.info("Getting available strategy list");
            
            String url = pythonServiceUrl + "/api/strategy/strategies";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("Strategy list response: {}", responseBody);
                

                if (responseBody.containsKey("data") && responseBody.get("data") instanceof java.util.List) {
                    java.util.List<?> strategies = (java.util.List<?>) responseBody.get("data");
                    return strategies.stream()
                        .map(Object::toString)
                        .toArray(String[]::new);
                }
            }
            

            return new String[]{"ElderSwingStrategy", "ElderIntradayStrategy"};
            
        } catch (Exception e) {
            log.warn("Failed to get strategy list", e);
            return new String[0];
        }
    }
}