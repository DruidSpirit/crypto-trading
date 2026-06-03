package druid.elf.tool.dto;

import lombok.Data;

@Data
public class SignalFilterDTO {
    private String search;
    private String signalType;
    private String strategy;
    private String exchange;
    private String startDate;
    private String endDate;
    private Integer page;
    private Integer size;
}