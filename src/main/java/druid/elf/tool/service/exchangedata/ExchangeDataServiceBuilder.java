package druid.elf.tool.service.exchangedata;

import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.enums.ExchangeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.util.Map;


@Component
public class ExchangeDataServiceBuilder {
    private SettingsProxy proxySettings;
    private ExchangeType exchangeType;

    @Autowired
    private ApplicationContext applicationContext;


    public ExchangeDataServiceBuilder() {
    }


    public ExchangeDataServiceBuilder withProxySettings(SettingsProxy proxySettings) {
        this.proxySettings = proxySettings;
        return this;
    }


    public ExchangeDataServiceBuilder withExchangeType(ExchangeType exchangeType) {
        this.exchangeType = exchangeType;
        return this;
    }


    public ExchangeDataService build() {

        if (proxySettings != null) {
            if (proxySettings.getIp() == null || proxySettings.getIp().isEmpty()) {
                throw new IllegalArgumentException("Proxy IP cannot be empty");
            }
            if (proxySettings.getPort() == null) {
                throw new IllegalArgumentException("Proxy port cannot be empty");
            }
        }
        if (exchangeType == null) {
            throw new IllegalArgumentException("Exchange type cannot be empty");
        }


        Map<String, ExchangeDataService> services = applicationContext.getBeansOfType(ExchangeDataService.class);
        for (ExchangeDataService service : services.values()) {
            if (service.getExchangeType() == exchangeType) {

                try {
                    return service.getClass()
                            .getConstructor(SettingsProxy.class)
                            .newInstance(proxySettings);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create " + exchangeType + " service instance", e);
                }
            }
        }
        throw new IllegalArgumentException("No matching service instance found for " + exchangeType);
    }
}