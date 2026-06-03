package druid.elf.tool.service;

import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.enums.ExchangeType;
import druid.elf.tool.service.exchangedata.ExchangeDataService;
import druid.elf.tool.service.exchangedata.ExchangeDataServiceBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DataService {

    @Autowired
    private ExchangeDataServiceBuilder exchangeDataServiceBuilder;


    public ExchangeDataService createExchangeDataService(ExchangeType exchangeType, SettingsProxy proxySettings) {

        ExchangeDataServiceBuilder builder = exchangeDataServiceBuilder
                .withExchangeType(exchangeType)
                .withProxySettings(proxySettings);


        try {
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to create exchange data service, exchangeType: {}", exchangeType, e);
            throw e;
        }
    }
}