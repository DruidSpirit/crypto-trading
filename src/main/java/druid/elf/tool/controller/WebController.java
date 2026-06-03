package druid.elf.tool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {


    @GetMapping("/")
    public String index() {
        return "index";
    }


    @GetMapping("/signals")
    public String signals() {
        return "signals";
    }


    @GetMapping("/strategies")
    public String strategies() {
        return "strategies";
    }


    @GetMapping("/backtest")
    public String backtest() {
        return "backtest";
    }


    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }
}