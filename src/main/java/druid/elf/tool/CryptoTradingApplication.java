package druid.elf.tool;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class CryptoTradingApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(CryptoTradingApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 在Spring Boot项目启动成功后打开浏览器并跳转到指定的URL
        String url = "http://localhost:5567/api/index"; // 替换为你需要的URL
        openBrowser(url);
    }

    private void openBrowser(String url) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();

        // Windows
        if (os.contains("win")) {
            Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
        }
        // MacOS
        else if (os.contains("mac")) {
            Runtime.getRuntime().exec("open " + url);
        }
        // Linux
        else if (os.contains("nix") || os.contains("nux")) {
            Runtime.getRuntime().exec("xdg-open " + url);
        }
        else {
            throw new UnsupportedOperationException("Unsupported operating system");
        }
    }

}
