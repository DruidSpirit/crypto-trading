package druid.elf.tool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate配置类
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 设置连接超时时间（30秒）
        factory.setConnectTimeout(Duration.ofSeconds(30));
        
        // 设置读取超时时间（60秒）
        factory.setReadTimeout(Duration.ofSeconds(60));
        
        return new RestTemplate(factory);
    }
}