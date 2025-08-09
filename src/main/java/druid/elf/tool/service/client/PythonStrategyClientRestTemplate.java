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

/**
 * 使用RestTemplate实现的Python策略客户端
 * 替代WebClient的实现方案
 */
@Service
@Slf4j
public class PythonStrategyClientRestTemplate {

    private final RestTemplate restTemplate;
    
    @Value("${python.strategy.service.url:http://localhost:8000}")
    private String pythonServiceUrl;

    public PythonStrategyClientRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PythonTradeSignalDTO executeStrategy(StrategyRequestDTO request) {
        try {
            log.info("调用Python策略服务，请求参数：{}", request);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            
            // 创建请求实体
            HttpEntity<StrategyRequestDTO> requestEntity = new HttpEntity<>(request, headers);
            
            // 发送请求
            String url = pythonServiceUrl + "/api/strategy/execute";
            ResponseEntity<StrategyResponseDTO> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                requestEntity, 
                StrategyResponseDTO.class
            );
            
            log.info("Python策略服务响应状态：{}", response.getStatusCode());
            log.info("Python策略服务响应：{}", response.getBody());
            
            StrategyResponseDTO responseBody = response.getBody();
            if (responseBody != null && responseBody.isSuccess()) {
                return responseBody.getData();
            } else {
                log.info("Python策略服务未生成交易信号或执行失败: {}", 
                    responseBody != null ? responseBody.getMessage() : "响应为空");
                return null;
            }
            
        } catch (RestClientException e) {
            log.error("调用Python策略服务失败", e);
            return null;
        } catch (Exception e) {
            log.error("调用Python策略服务失败", e);
            throw new RuntimeException("调用Python策略服务失败: " + e.getMessage(), e);
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
            log.warn("Python策略服务健康检查失败", e);
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    public String[] getAvailableStrategies() {
        try {
            log.info("获取可用策略列表");
            
            String url = pythonServiceUrl + "/api/strategy/strategies";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("获取策略列表响应：{}", responseBody);
                
                // 从响应中提取策略列表
                if (responseBody.containsKey("data") && responseBody.get("data") instanceof java.util.List) {
                    java.util.List<?> strategies = (java.util.List<?>) responseBody.get("data");
                    return strategies.stream()
                        .map(Object::toString)
                        .toArray(String[]::new);
                }
            }
            
            // 返回默认策略
            return new String[]{"SimpleMacdCrossStrategy"};
            
        } catch (Exception e) {
            log.warn("获取策略列表失败", e);
            return new String[0];
        }
    }
}