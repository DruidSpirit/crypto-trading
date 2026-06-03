package druid.elf.tool.repository;

import druid.elf.tool.entity.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradingPairRepository extends JpaRepository<TradingPair, String> {


    List<TradingPair> findByExchange(String exchange);

}
