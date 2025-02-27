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

    /**
     * 创建交易所数据服务的实例
     * @param exchangeType 交易所类型
     * @param proxySettings 代理设置对象，可以为null表示不使用代理
     * @return ExchangeDataService 实例
     */
    public ExchangeDataService createExchangeDataService(ExchangeType exchangeType, SettingsProxy proxySettings) {
        // 使用获取到的配置构建 ExchangeDataServiceBuilder
        ExchangeDataServiceBuilder builder = exchangeDataServiceBuilder
                .withExchangeType(exchangeType)
                .withProxySettings(proxySettings);

        // 构建并返回 ExchangeDataService 实例
        try {
            return builder.build();
        } catch (Exception e) {
            log.error("创建交易所数据服务失败, exchangeType: {}", exchangeType, e);
            throw e;
        }
    }
}