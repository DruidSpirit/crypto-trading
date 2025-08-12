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
    
    @Value("${python.strategy.service.url:http://localhost:8000}")
    private String pythonServiceUrl;

    public PythonStrategyClient() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public PythonTradeSignalDTO executeStrategy(StrategyRequestDTO request) {
        try {
            log.info("调用Python策略服务，请求参数：{}", request);
            
            StrategyResponseDTO response = webClient
                    .post()
                    .uri(pythonServiceUrl + "/api/strategy/execute")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(StrategyResponseDTO.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
                    
            log.info("Python策略服务响应：{}", response);
            
            if (response != null && response.isSuccess()) {
                return response.getData();
            } else {
                log.info("Python策略服务未生成交易信号或执行失败: {}", 
                    response != null ? response.getMessage() : "响应为空");
                return null;
            }
            
        } catch (WebClientResponseException e) {
            log.error("调用Python策略服务失败，HTTP状态码：{}，响应体：{}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("调用Python策略服务失败", e);
            throw new RuntimeException("调用Python策略服务失败: " + e.getMessage(), e);
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
            log.warn("Python策略服务健康检查失败", e);
            return false;
        }
    }
    
    public String[] getAvailableStrategies() {
        try {
            log.info("获取可用策略列表");
            
            String response = webClient
                    .get()
                    .uri(pythonServiceUrl + "/api/strategy/strategies")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
                    
            log.info("获取策略列表响应：{}", response);
            // 这里可以解析JSON返回策略数组，暂时返回默认策略
            return new String[]{"ElderSwingStrategy", "ElderIntradayStrategy"};
            
        } catch (Exception e) {
            log.warn("获取策略列表失败", e);
            return new String[0];
        }
    }
}