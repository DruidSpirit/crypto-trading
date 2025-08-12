package druid.elf.tool.dto;

import lombok.Data;

@Data
public class BacktestResponseDTO {
    private boolean success;
    private String message;
    private BacktestResultDTO data;
}