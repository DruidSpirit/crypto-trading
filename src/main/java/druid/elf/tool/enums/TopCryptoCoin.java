package druid.elf.tool.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum TopCryptoCoin {
    BTC("BTC", Arrays.asList("USDT", "ETH")),






    ETH("ETH", Arrays.asList("USDT", "BTC")),






    BNB("BNB", Arrays.asList("USDT")),





    SOL("SOL", Arrays.asList("USDT")),





    FIL("FIL", Arrays.asList("USDT")),





    MKR("MKR", Arrays.asList("USDT")),




    UNI("UNI", Arrays.asList("USDT")),




    AAVE("AAVE", Arrays.asList("USDT")),




    LINK("LINK", Arrays.asList("USDT")),




    DOT("DOT", Arrays.asList("USDT")),





    NEAR("NEAR", Arrays.asList("USDT")),




    APT("APT", Arrays.asList("USDT")),






    ZEC("ZEC", Arrays.asList("USDT")),





    XLM("XLM", Arrays.asList("USDT")),




    TAO("TAO", Arrays.asList("USDT")),




    SUI("SUI", Arrays.asList("USDT"));







    private final String symbol;
    private final List<String> tradedAgainst;

    public static List<String> getAllSymbols() {
        return Arrays.stream(values())
                .map(TopCryptoCoin::getSymbol)
                .collect(Collectors.toList());
    }
}