package druid.elf.tool.repository;

import druid.elf.tool.entity.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradingPairRepository extends JpaRepository<TradingPair, String> {

    // 按交易所查找交易对
    List<TradingPair> findByExchange(String exchange);

}
