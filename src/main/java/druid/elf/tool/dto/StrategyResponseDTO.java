package druid.elf.tool.dto;

import lombok.Data;

@Data
public class StrategyResponseDTO {
    private boolean success;
    private PythonTradeSignalDTO data;
    private String message;
}