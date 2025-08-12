package druid.elf.tool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    /**
     * 首页 - 保持原有路径不变
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 信号列表页面
     */
    @GetMapping("/signals")
    public String signals() {
        return "signals";
    }

    /**
     * 策略管理页面
     */
    @GetMapping("/strategies")
    public String strategies() {
        return "strategies";
    }

    /**
     * 策略回测页面
     */
    @GetMapping("/backtest")
    public String backtest() {
        return "backtest";
    }

    /**
     * 系统设置页面
     */
    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }
}