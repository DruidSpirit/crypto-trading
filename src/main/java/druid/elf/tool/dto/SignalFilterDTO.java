package druid.elf.tool.dto;

import lombok.Data;

@Data
public class SignalFilterDTO {
    private String search;      // 按交易对搜索
    private String signalType;  // 信号类型 (BUY/SELL)
    private String strategy;    // 策略名称
    private String exchange;    // 字符串，ExchangeType 枚举名称
    private String startDate;   // 开始日期 (格式: YYYY-MM-DD)
    private String endDate;     // 结束日期 (格式: YYYY-MM-DD)
    private Integer page;       // 页码 (从1开始)
    private Integer size;       // 每页大小
}