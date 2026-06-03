package druid.elf.tool.service.exchangedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import druid.elf.tool.entity.SettingsProxy;
import druid.elf.tool.entity.TradingPair;
import druid.elf.tool.enums.KlineInterval;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractExchangeDataService implements ExchangeDataService {
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper = new ObjectMapper();
    private static final Random RANDOM = new Random();
    private static final int MAX_RETRIES = 4;
    private static final int BASE_DELAY_MS = 500;
    private static final int RANDOM_DELAY_RANGE = 1500;


    protected AbstractExchangeDataService(SettingsProxy proxySettings) {
        OkHttpClient.Builder builder = createBaseClientBuilder();


        if (proxySettings != null && isValidProxy(proxySettings)) {
            log.debug("Configuring proxy: {}:{}", proxySettings.getIp(), proxySettings.getPort());
            configureProxy(builder, proxySettings);
            configureTLS(builder);
        } else {
            log.debug("No valid proxy provided, using default config");
        }

        this.client = builder.build();
        log.debug("OkHttpClient initialization complete");
    }


    private OkHttpClient.Builder createBaseClientBuilder() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES));
    }


    private boolean isValidProxy(SettingsProxy proxySettings) {
        if (proxySettings.getIp() == null || proxySettings.getPort() == null) {
            log.warn("Proxy IP or port invalid");
            return false;
        }
        try {
            java.net.InetAddress.getByName(proxySettings.getIp());
            return proxySettings.getPort() > 0 && proxySettings.getPort() <= 65535;
        } catch (java.net.UnknownHostException e) {
            log.warn("Proxy IP invalid: {}", proxySettings.getIp());
            return false;
        }
    }


    private void configureProxy(OkHttpClient.Builder builder, SettingsProxy proxySettings) {
        Proxy.Type proxyType = determineProxyType(proxySettings.getType());
        Proxy proxy = new Proxy(proxyType, new InetSocketAddress(proxySettings.getIp(), proxySettings.getPort()));
        builder.proxy(proxy);

        if (hasValidCredentials(proxySettings)) {
            configureProxyAuthentication(builder, proxySettings);
        }
    }


    private Proxy.Type determineProxyType(String type) {
        if (type == null) return Proxy.Type.HTTP;
        switch (type.toUpperCase()) {
            case "SOCKET":
            case "SOCKS":
            case "SOCKS5":
                return Proxy.Type.SOCKS;
            case "HTTP":
            case "HTTPS":
                return Proxy.Type.HTTP;
            default:
                log.warn("Unsupported proxy type: {}, defaulting to HTTP proxy", type);
                return Proxy.Type.HTTP;
        }
    }


    private boolean hasValidCredentials(SettingsProxy proxySettings) {
        return proxySettings.getUsername() != null && !proxySettings.getUsername().isEmpty() &&
                proxySettings.getPassword() != null && !proxySettings.getPassword().isEmpty();
    }


    private void configureProxyAuthentication(OkHttpClient.Builder builder, SettingsProxy proxySettings) {
        log.debug("Configuring proxy authentication: username {}", proxySettings.getUsername().substring(0, Math.min(2, proxySettings.getUsername().length())) + "***");
        builder.proxyAuthenticator((route, response) -> {
            String credential = Credentials.basic(proxySettings.getUsername(), proxySettings.getPassword());
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        });
    }


    private void configureTLS(OkHttpClient.Builder builder) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            X509TrustManager trustManager = getDefaultTrustManager();
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("TLS configuration failed: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to initialize TLS configuration", e);
        }
    }


    private X509TrustManager getDefaultTrustManager() throws NoSuchAlgorithmException {
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory
                .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        try {
            tmf.init((java.security.KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    log.debug("Successfully obtained default X509TrustManager");
                    return (X509TrustManager) tm;
                }
            }
            throw new NoSuchAlgorithmException("No X509TrustManager found");
        } catch (Exception e) {
            log.error("Failed to get TrustManager: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public BarSeries getKlineData(String symbol, KlineInterval interval, int dataCount) throws IOException {
        String intervalSymbol = interval.getInterval(this.getExchangeType());
        String url = buildUrl(symbol, intervalSymbol, dataCount);
        return parseKlineData(executeRequest(url));
    }

    @Override
    public List<TradingPair> getTradingPairs() throws IOException {
        String url = buildTradingPairsUrl();
        return fetchTradingPairs(executeRequest(url));
    }


    protected String executeRequest(String url) throws IOException {
        applyRandomDelay();
        Request request = buildRequest(url);
        log.info("{} Request URL: {}", getExchangeType(), url);

        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                log.debug("Received response, status code: {}", response.code());
                return processResponse(response, attempt);
            } catch (IOException e) {
                lastException = e;
                if (!shouldRetry(e, attempt)) {
                    log.error("Non-retryable error: {}", e.getMessage(), e);
                    throw e;
                }
                long retryDelay = calculateRetryDelay(attempt, e);
                log.warn("Request failed, attempt {} retry, waiting {}ms: {}", attempt, retryDelay, e.getMessage());
                sleep(retryDelay);
            }
        }
        throw new IOException("Request failed, exceeded retry count", lastException);
    }


    private void applyRandomDelay() throws IOException {
        int delay = BASE_DELAY_MS + RANDOM.nextInt(RANDOM_DELAY_RANGE);
        log.debug("Random delay before request: {}ms", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Random delay interrupted", e);
        }
    }


    private Request buildRequest(String url) {
        String randomUserAgent = UserAgents.USER_AGENTS.get(RANDOM.nextInt(UserAgents.USER_AGENTS.size()));
        String randomIp = generateRandomIp();
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", randomUserAgent)
                .addHeader("X-Forwarded-For", randomIp)
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Connection", "keep-alive")
                .build();
    }


    private String processResponse(Response response, int attempt) throws IOException {
        if (!response.isSuccessful()) {
            handleUnsuccessfulResponse(response, attempt);
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            throw new IOException("Response body is empty");
        }
        String bodyString = responseBody.string();
        log.info("Response from {}: {}", response.request().url(), truncateResponse(bodyString));
        return bodyString;
    }


    private void handleUnsuccessfulResponse(Response response, int attempt) throws IOException {
        int code = response.code();
        if (code == 429 && attempt < MAX_RETRIES) {
            throw new IOException("Too Many Requests (429)");
        }
        if (code == 403 || code == 409) {
            throw new IOException("Forbidden or Conflict (" + code + ")");
        }
        throw new IOException("Request failed: " + code + " " + response.message());
    }


    private boolean shouldRetry(IOException e, int attempt) {
        if (attempt >= MAX_RETRIES) return false;
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return errorMsg.contains("remote host terminated the handshake") ||
                errorMsg.contains("ssl peer shut down incorrectly") ||
                e instanceof java.net.SocketTimeoutException ||
                e instanceof java.net.ConnectException ||
                errorMsg.contains("(429)") ||
                errorMsg.contains("(403)") ||
                errorMsg.contains("(409)");
    }


    private long calculateRetryDelay(int attempt, IOException e) {
        double factor = 1 + 0.5 * (attempt - 1);
        String errorMsg = e.getMessage().toLowerCase();
        if (errorMsg.contains("remote host terminated the handshake") ||
                errorMsg.contains("(403)") || errorMsg.contains("(409)")) {
            return (long) ((10000 + RANDOM.nextInt(20000)) * factor);
        }
        if (errorMsg.contains("(429)")) {
            return (long) ((500 + RANDOM.nextInt(1000)) * factor);
        }
        return (long) ((1000 + RANDOM.nextInt(2000)) * factor);
    }


    private void sleep(long delay) throws IOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log.error("Retry wait interrupted: {}", e.getMessage(), e);
            throw new IOException("Retry wait interrupted", e);
        }
    }


    private String truncateResponse(String response) {
        return response.length() > 200 ? response.substring(0, 200) + "..." : response;
    }


    private String generateRandomIp() {
        return String.format("%d.%d.%d.%d",
                RANDOM.nextInt(223) + 1,
                RANDOM.nextInt(256),
                RANDOM.nextInt(256),
                RANDOM.nextInt(256));
    }

    protected abstract String buildUrl(String symbol, String interval, int dataCount);
    protected abstract BarSeries parseKlineData(String responseBody) throws IOException;
    protected abstract String buildTradingPairsUrl();
    protected abstract List<TradingPair> fetchTradingPairs(String responseBody) throws IOException;


    protected BaseBar buildBar(long timestamp, double open, double high, double low, double close, double volume) {
        ZonedDateTime endTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault());
        return new BaseBar(
                Duration.ofMinutes(1), endTime,
                DecimalNum.valueOf(open), DecimalNum.valueOf(high), DecimalNum.valueOf(low),
                DecimalNum.valueOf(close), DecimalNum.valueOf(volume), DecimalNum.valueOf(0.0), 0L
        );
    }
}