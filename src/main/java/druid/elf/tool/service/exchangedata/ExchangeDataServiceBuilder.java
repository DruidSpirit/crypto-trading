package druid.elf.tool.service.exchangedata;

import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.enums.ExchangeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 交易所数据服务构造者，使用构造者模式动态创建服务实例
 */
@Component
public class ExchangeDataServiceBuilder {
    private SettingsProxy proxySettings;    // 代理设置对象
    private ExchangeType exchangeType;      // 交易所类型

    @Autowired
    private ApplicationContext applicationContext; // Spring 上下文，用于获取容器中的实例

    /**
     * 默认构造函数
     */
    public ExchangeDataServiceBuilder() {
    }

    /**
     * 设置代理配置
     */
    public ExchangeDataServiceBuilder withProxySettings(SettingsProxy proxySettings) {
        this.proxySettings = proxySettings;
        return this;
    }

    /**
     * 设置交易所类型
     */
    public ExchangeDataServiceBuilder withExchangeType(ExchangeType exchangeType) {
        this.exchangeType = exchangeType;
        return this;
    }

    /**
     * 构建交易所数据服务实例
     *
     * @return ExchangeDataService 实例
     */
    public ExchangeDataService build() {
        // 验证参数
        if (proxySettings != null) {
            if (proxySettings.getIp() == null || proxySettings.getIp().isEmpty()) {
                throw new IllegalArgumentException("代理IP不能为空");
            }
            if (proxySettings.getPort() == null) {
                throw new IllegalArgumentException("代理端口不能为空");
            }
        }
        if (exchangeType == null) {
            throw new IllegalArgumentException("交易所类型不能为空");
        }

        // 从Spring容器中获取所有实现了ExchangeDataService的实例
        Map<String, ExchangeDataService> services = applicationContext.getBeansOfType(ExchangeDataService.class);
        for (ExchangeDataService service : services.values()) {
            if (service.getExchangeType() == exchangeType) {
                // 使用反射创建新实例，传入SettingsProxy参数
                try {
                    return service.getClass()
                            .getConstructor(SettingsProxy.class)
                            .newInstance(proxySettings);
                } catch (Exception e) {
                    throw new RuntimeException("创建 " + exchangeType + " 服务实例失败", e);
                }
            }
        }
        throw new IllegalArgumentException("未找到匹配 " + exchangeType + " 的服务实例");
    }
}