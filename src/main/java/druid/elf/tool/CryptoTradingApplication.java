package druid.elf.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(CryptoTradingApplication.class);

    @Value("${app.browser.enabled:true}")
    private boolean browserEnabled;

    public static void main(String[] args) {
        SpringApplication.run(CryptoTradingApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!browserEnabled) {
            log.info("Automatic browser launch is disabled");
            return;
        }

        // Open the dashboard after the Spring Boot application starts.
        String url = "http://localhost:5567/api/index";
        try {
            openBrowser(url);
        } catch (Exception e) {
            log.warn("Failed to open browser automatically: {}", e.getMessage());
        }
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
