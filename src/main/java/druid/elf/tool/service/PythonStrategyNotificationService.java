package druid.elf.tool.service;

import druid.elf.tool.entity.StrategyFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PythonStrategyNotificationService {
    
    private final RestTemplate restTemplate;
    
    @Value("${python.strategy.api.base-url:http://localhost:5000}")
    private String pythonApiBaseUrl;
    
    public PythonStrategyNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public void notifyStrategyUpload(StrategyFile strategyFile) {
        String url = pythonApiBaseUrl + "/api/strategy/upload";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("filename", strategyFile.getFilename());
        payload.put("originalFilename", strategyFile.getOriginalFilename());
        payload.put("filePath", strategyFile.getFilePath());
        payload.put("description", strategyFile.getDescription());
        payload.put("action", "upload");
        
        sendNotification(url, payload);
    }
    
    public void notifyStrategyDelete(StrategyFile strategyFile) {
        String url = pythonApiBaseUrl + "/api/strategy/delete";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("filename", strategyFile.getFilename());
        payload.put("filePath", strategyFile.getFilePath());
        payload.put("action", "delete");
        
        sendNotification(url, payload);
    }
    
    public void notifyStrategyHotReload(StrategyFile strategyFile) {
        String url = pythonApiBaseUrl + "/api/strategy/reload";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("filename", strategyFile.getFilename());
        payload.put("filePath", strategyFile.getFilePath());
        payload.put("action", "reload");
        
        sendNotification(url, payload);
    }
    
    private void sendNotification(String url, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            String response = restTemplate.postForObject(url, request, String.class);
            log.info("Python API响应: {}", response);
        } catch (Exception e) {
            log.error("发送通知到Python API失败: {}", url, e);
            throw new RuntimeException("通知Python项目失败", e);
        }
    }
}