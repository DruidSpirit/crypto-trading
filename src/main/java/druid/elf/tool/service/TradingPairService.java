package druid.elf.tool.service;

import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.repository.TradingPairRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TradingPairService {

    @Autowired
    private TradingPairRepository repository;

    /**
     * 获取所有交易对
     */
    public List<TradingPair> getAllTradingPairs() {
        return repository.findAll();
    }

}