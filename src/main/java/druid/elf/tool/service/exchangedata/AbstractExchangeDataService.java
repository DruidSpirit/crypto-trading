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
    protected final OkHttpClient client;                    // HTTP客户端，用于发送网络请求
    protected final ObjectMapper objectMapper = new ObjectMapper(); // JSON解析工具
    private static final Random RANDOM = new Random();      // 随机数生成器，用于生成随机延迟和IP
    private static final int MAX_RETRIES = 4;               // 最大重试次数
    private static final int BASE_DELAY_MS = 500;           // 基础延迟时间（毫秒）
    private static final int RANDOM_DELAY_RANGE = 1500;     // 随机延迟范围（毫秒）

    /**
     * 构造函数，初始化HTTP客户端并配置代理和TLS
     * @param proxySettings 代理设置，可能为空
     */
    protected AbstractExchangeDataService(SettingsProxy proxySettings) {
        OkHttpClient.Builder builder = createBaseClientBuilder();

        // 检查是否有有效的代理设置
        if (proxySettings != null && isValidProxy(proxySettings)) {
            log.debug("配置代理: {}:{}", proxySettings.getIp(), proxySettings.getPort());
            configureProxy(builder, proxySettings); // 配置代理
            configureTLS(builder);                  // 配置TLS加密
        } else {
            log.debug("未提供有效代理，使用默认配置");
        }

        this.client = builder.build();
        log.debug("OkHttpClient 初始化完成");
    }

    /**
     * 创建基础的OkHttpClient构建器，设定超时和连接池
     * @return 配置好的构建器
     */
    private OkHttpClient.Builder createBaseClientBuilder() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)       // 连接超时60秒
                .readTimeout(60, TimeUnit.SECONDS)          // 读取超时60秒
                .writeTimeout(60, TimeUnit.SECONDS)         // 写入超时60秒
                .retryOnConnectionFailure(true)             // 连接失败时自动重试
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES)); // 连接池配置
    }

    /**
     * 验证代理设置是否有效
     * @param proxySettings 代理设置
     * @return 是否有效
     */
    private boolean isValidProxy(SettingsProxy proxySettings) {
        if (proxySettings.getIp() == null || proxySettings.getPort() == null) {
            log.warn("代理IP或端口为空");
            return false;
        }
        try {
            java.net.InetAddress.getByName(proxySettings.getIp()); // 检查IP是否可解析
            return proxySettings.getPort() > 0 && proxySettings.getPort() <= 65535; // 验证端口范围
        } catch (java.net.UnknownHostException e) {
            log.warn("代理IP无效: {}", proxySettings.getIp());
            return false;
        }
    }

    /**
     * 配置代理，包括类型和认证
     * @param builder HTTP客户端构建器
     * @param proxySettings 代理设置
     */
    private void configureProxy(OkHttpClient.Builder builder, SettingsProxy proxySettings) {
        Proxy.Type proxyType = determineProxyType(proxySettings.getType()); // 确定代理类型
        Proxy proxy = new Proxy(proxyType, new InetSocketAddress(proxySettings.getIp(), proxySettings.getPort()));
        builder.proxy(proxy); // 设置代理

        if (hasValidCredentials(proxySettings)) {
            configureProxyAuthentication(builder, proxySettings); // 配置代理认证
        }
    }

    /**
     * 根据代理类型字符串确定代理类型
     * @param type 代理类型字符串
     * @return Proxy.Type 枚举值
     */
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
                log.warn("不支持的代理类型: {}, 默认使用HTTP代理", type);
                return Proxy.Type.HTTP;
        }
    }

    /**
     * 检查代理是否需要认证
     * @param proxySettings 代理设置
     * @return 是否有有效的用户名和密码
     */
    private boolean hasValidCredentials(SettingsProxy proxySettings) {
        return proxySettings.getUsername() != null && !proxySettings.getUsername().isEmpty() &&
                proxySettings.getPassword() != null && !proxySettings.getPassword().isEmpty();
    }

    /**
     * 配置代理认证
     * @param builder HTTP客户端构建器
     * @param proxySettings 代理设置
     */
    private void configureProxyAuthentication(OkHttpClient.Builder builder, SettingsProxy proxySettings) {
        log.debug("配置代理认证: 用户名 {}", proxySettings.getUsername().substring(0, Math.min(2, proxySettings.getUsername().length())) + "***");
        builder.proxyAuthenticator((route, response) -> {
            String credential = Credentials.basic(proxySettings.getUsername(), proxySettings.getPassword());
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        });
    }

    /**
     * 配置TLS加密
     * @param builder HTTP客户端构建器
     */
    private void configureTLS(OkHttpClient.Builder builder) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            X509TrustManager trustManager = getDefaultTrustManager();
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("TLS配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法初始化TLS配置", e);
        }
    }

    /**
     * 获取默认的X509信任管理器
     * @return X509TrustManager
     * @throws NoSuchAlgorithmException 如果算法不可用
     */
    private X509TrustManager getDefaultTrustManager() throws NoSuchAlgorithmException {
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory
                .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        try {
            tmf.init((java.security.KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    log.debug("成功获取默认X509TrustManager");
                    return (X509TrustManager) tm;
                }
            }
            throw new NoSuchAlgorithmException("No X509TrustManager found");
        } catch (Exception e) {
            log.error("获取TrustManager失败: {}", e.getMessage(), e);
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

    /**
     * 执行HTTP请求，包含重试逻辑
     * @param url 请求地址
     * @return 响应体的字符串形式
     * @throws IOException 如果请求失败
     */
    protected String executeRequest(String url) throws IOException {
        applyRandomDelay(); // 应用随机延迟
        Request request = buildRequest(url); // 构建请求
        log.info("{}请求地址: {}", getExchangeType(), url);

        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                log.debug("收到响应，状态码: {}", response.code());
                return processResponse(response, attempt); // 处理响应
            } catch (IOException e) {
                lastException = e;
                if (!shouldRetry(e, attempt)) {
                    log.error("非可重试错误: {}", e.getMessage(), e);
                    throw e;
                }
                long retryDelay = calculateRetryDelay(attempt, e); // 计算重试延迟
                log.warn("请求失败，第 {} 次重试，等待 {}ms: {}", attempt, retryDelay, e.getMessage());
                sleep(retryDelay); // 执行延迟
            }
        }
        throw new IOException("请求失败，超出重试次数", lastException);
    }

    /**
     * 在请求前应用随机延迟
     * @throws IOException 如果线程中断
     */
    private void applyRandomDelay() throws IOException {
        int delay = BASE_DELAY_MS + RANDOM.nextInt(RANDOM_DELAY_RANGE);
        log.debug("请求前随机等待 {}ms", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("随机延迟被中断", e);
        }
    }

    /**
     * 构建HTTP请求，设置随机头信息
     * @param url 请求地址
     * @return 配置好的Request对象
     */
    private Request buildRequest(String url) {
        String randomUserAgent = UserAgents.USER_AGENTS.get(RANDOM.nextInt(UserAgents.USER_AGENTS.size()));
        String randomIp = generateRandomIp();
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", randomUserAgent)       // 随机用户代理
                .addHeader("X-Forwarded-For", randomIp)         // 随机IP
                .addHeader("Accept", "application/json")        // 接受JSON格式
                .addHeader("Accept-Language", "en-US,en;q=0.9") // 语言偏好
                .addHeader("Connection", "keep-alive")          // 保持连接
                .build();
    }

    /**
     * 处理HTTP响应
     * @param response 响应对象
     * @param attempt 当前尝试次数
     * @return 响应体的字符串形式
     * @throws IOException 如果响应处理失败
     */
    private String processResponse(Response response, int attempt) throws IOException {
        if (!response.isSuccessful()) {
            handleUnsuccessfulResponse(response, attempt); // 处理失败响应
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            throw new IOException("响应体为空");
        }
        String bodyString = responseBody.string();
        log.info("Response from {}: {}", response.request().url(), truncateResponse(bodyString));
        return bodyString;
    }

    /**
     * 处理不成功的响应
     * @param response 响应对象
     * @param attempt 当前尝试次数
     * @throws IOException 根据状态码抛出异常
     */
    private void handleUnsuccessfulResponse(Response response, int attempt) throws IOException {
        int code = response.code();
        if (code == 429 && attempt < MAX_RETRIES) {
            throw new IOException("Too Many Requests (429)");
        }
        if (code == 403 || code == 409) {
            throw new IOException("Forbidden or Conflict (" + code + ")");
        }
        throw new IOException("请求失败: " + code + " " + response.message());
    }

    /**
     * 判断是否需要重试
     * @param e 异常对象
     * @param attempt 当前尝试次数
     * @return 是否继续重试
     */
    private boolean shouldRetry(IOException e, int attempt) {
        if (attempt >= MAX_RETRIES) return false;
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return errorMsg.contains("remote host terminated the handshake") || // 远程主机终止握手
                errorMsg.contains("ssl peer shut down incorrectly") ||      // SSL异常
                e instanceof java.net.SocketTimeoutException ||             // 超时
                e instanceof java.net.ConnectException ||                   // 连接异常
                errorMsg.contains("(429)") ||                               // 限流
                errorMsg.contains("(403)") ||                               // 禁止访问
                errorMsg.contains("(409)");                                 // 冲突
    }

    /**
     * 计算重试延迟时间
     * @param attempt 当前尝试次数
     * @param e 异常对象
     * @return 延迟时间（毫秒）
     */
    private long calculateRetryDelay(int attempt, IOException e) {
        double factor = 1 + 0.5 * (attempt - 1); // 指数退避因子：1, 1.5, 2, 2.5
        String errorMsg = e.getMessage().toLowerCase();
        if (errorMsg.contains("remote host terminated the handshake") ||
                errorMsg.contains("(403)") || errorMsg.contains("(409)")) {
            return (long) ((10000 + RANDOM.nextInt(20000)) * factor); // 10-30秒
        }
        if (errorMsg.contains("(429)")) {
            return (long) ((500 + RANDOM.nextInt(1000)) * factor); // 0.5-1.5秒
        }
        return (long) ((1000 + RANDOM.nextInt(2000)) * factor); // 1-3秒
    }

    /**
     * 执行线程睡眠
     * @param delay 睡眠时间（毫秒）
     * @throws IOException 如果睡眠被中断
     */
    private void sleep(long delay) throws IOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log.error("重试等待被中断: {}", e.getMessage(), e);
            throw new IOException("重试等待被中断", e);
        }
    }

    /**
     * 截断响应字符串，便于日志记录
     * @param response 响应字符串
     * @return 截断后的字符串
     */
    private String truncateResponse(String response) {
        return response.length() > 200 ? response.substring(0, 200) + "..." : response;
    }

    /**
     * 生成随机IP地址
     * @return 随机IP字符串
     */
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

    /**
     * 构建K线数据条目
     * @param timestamp 时间戳
     * @param open 开盘价
     * @param high 最高价
     * @param low 最低价
     * @param close 收盘价
     * @param volume 交易量
     * @return BaseBar对象
     */
    protected BaseBar buildBar(long timestamp, double open, double high, double low, double close, double volume) {
        ZonedDateTime endTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault());
        return new BaseBar(
                Duration.ofMinutes(1), endTime,
                DecimalNum.valueOf(open), DecimalNum.valueOf(high), DecimalNum.valueOf(low),
                DecimalNum.valueOf(close), DecimalNum.valueOf(volume), DecimalNum.valueOf(0.0), 0L
        );
    }
}