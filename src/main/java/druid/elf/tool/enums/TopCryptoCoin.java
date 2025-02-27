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
    // 比特币：主流交易对包括USDT和ETH
    // 中文介绍：
    // 比特币（Bitcoin）是第一种去中心化数字货币，
    // 由中本聪在2009年创建。
    // 它是市值最高、最广为人知的加密货币，
    // 被誉为“数字黄金”。
    ETH("ETH", Arrays.asList("USDT", "BTC")),
    // 以太坊：主流交易对包括USDT和BTC
    // 中文介绍：
    // 以太坊（Ethereum）是一个开源区块链平台，
    // 支持智能合约功能，
    // 由Vitalik Buterin于2015年推出，
    // 是第二大加密货币。
    BNB("BNB", Arrays.asList("USDT")),
    // 币安币：仅USDT交易对
    // 中文介绍：
    // 币安币（Binance Coin）由币安交易所发行，
    // 最初用于支付交易费用，
    // 现已扩展到币安生态系统的多种用途。
    SOL("SOL", Arrays.asList("USDT")),
    // 索拉纳：仅USDT交易对
    // 中文介绍：
    // 索拉纳（Solana）是一个高性能区块链，
    // 专注于快速交易和低成本，
    // 由Anatoly Yakovenko于2020年推出。
    FIL("FIL", Arrays.asList("USDT")),
    // Filecoin：仅USDT交易对
    // 中文介绍：
    // Filecoin是一个去中心化存储网络，
    // 旨在让用户通过加密货币支付来存储和检索数据，
    // 于2020年上线。
    MKR("MKR", Arrays.asList("USDT")),
    // MakerDAO：仅USDT交易对
    // 中文介绍：
    // Maker（MKR）是MakerDAO系统的治理代币，
    // 用于管理稳定币DAI的去中心化金融（DeFi）生态。
    UNI("UNI", Arrays.asList("USDT")),
    // Uniswap：仅USDT交易对
    // 中文介绍：
    // Uniswap（UNI）是一个去中心化交易协议的治理代币，
    // 允许用户无需中介直接交换加密货币。
    AAVE("AAVE", Arrays.asList("USDT")),
    // Aave：仅USDT交易对
    // 中文介绍：
    // Aave是一个去中心化借贷平台，
    // 用户可以通过其代币AAVE参与治理并赚取奖励。
    LINK("LINK", Arrays.asList("USDT")),
    // Chainlink：仅USDT交易对
    // 中文介绍：
    // Chainlink（LINK）是一个去中心化预言机网络，
    // 用于将现实世界的数据连接到区块链智能合约。
    DOT("DOT", Arrays.asList("USDT")),
    // Polkadot：仅USDT交易对
    // 中文介绍：
    // 波卡（Polkadot）是一个跨链协议，
    // 旨在连接不同的区块链，
    // 由Gavin Wood于2020年推出。
    NEAR("NEAR", Arrays.asList("USDT")),
    // Near Protocol：仅USDT交易对
    // 中文介绍：
    // NEAR Protocol是一个易于开发者使用的区块链平台，
    // 注重高扩展性和低成本交易。
    APT("APT", Arrays.asList("USDT")),
    // Aptos：仅USDT交易对
    // 中文介绍：
    // Aptos是一个新兴的Layer 1区块链，
    // 由前Meta员工开发，
    // 专注于安全性和可扩展性，
    // 于2022年推出。
    ZEC("ZEC", Arrays.asList("USDT")),
    // Zcash：仅USDT交易对
    // 中文介绍：
    // Zcash是一个注重隐私的加密货币，
    // 使用零知识证明技术保护交易细节，
    // 于2016年推出。
    XLM("XLM", Arrays.asList("USDT")),
    // Stellar Lumens：仅USDT交易对
    // 中文介绍：
    // 恒星币（Stellar Lumens）是一个用于跨境支付的区块链平台，
    // 由Jed McCaleb于2014年创建。
    TAO("TAO", Arrays.asList("USDT")),
    // Bittensor：仅USDT交易对
    // 中文介绍：
    // Bittensor（TAO）是一个去中心化AI网络的代币，
    // 旨在通过区块链技术推动机器学习的发展。
    SUI("SUI", Arrays.asList("USDT"));
    // Sui：仅USDT交易对
    // 中文介绍：
    // Sui是一个高性能Layer 1区块链，
    // 由Mysten Labs开发，
    // 注重速度和用户体验，
    // 于2023年上线。

    private final String symbol;
    private final List<String> tradedAgainst;  // 表示可以用哪些货币购买此币种

    public static List<String> getAllSymbols() {
        return Arrays.stream(values())
                .map(TopCryptoCoin::getSymbol)
                .collect(Collectors.toList());
    }
}