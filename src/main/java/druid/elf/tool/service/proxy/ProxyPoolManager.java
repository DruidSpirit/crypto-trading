package druid.elf.tool.service.proxy;

import druid.elf.tool.entity.SettingsProxy;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 代理池管理器，支持代理分配和任务执行，兼容无代理单线程模式
 */
@Slf4j
public class ProxyPoolManager {
    private final List<SettingsProxy> proxies;           // 代理列表
    private final ExecutorService threadPool;            // 线程池
    private final Map<String, Boolean> proxyStatus;      // 代理状态：true=占用，false=空闲
    private final ReentrantLock lock = new ReentrantLock(); // 分配锁
    private final ThreadLocal<SettingsProxy> proxyContext = new ThreadLocal<>(); // 线程上下文代理
    private final boolean noProxyMode;                   // 无代理模式标志

    /**
     * 构造方法：初始化代理池和线程池，支持无代理时单线程
     */
    public ProxyPoolManager(List<SettingsProxy> proxies) {
        this.proxies = proxies != null ? proxies : List.of();
        this.proxyStatus = new ConcurrentHashMap<>();
        this.noProxyMode = this.proxies.isEmpty();

        if (!noProxyMode) {
            this.proxies.forEach(proxy -> proxyStatus.put(proxy.getId(), false));
        }

        // 线程池大小：无代理时为1（单线程），有代理时为代理数量
        int poolSize = noProxyMode ? 1 : this.proxies.size();
        this.threadPool = new ThreadPoolExecutor(
                poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger threadNum = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "Proxy-Thread-" + threadNum.getAndIncrement());
                    }
                }
        );
        log.info("代理池初始化完成，代理数量: {}, 线程池大小: {}", this.proxies.size(), poolSize);
    }

    /**
     * 提交任务：返回CompletableFuture以支持等待所有任务完成
     */
    public CompletableFuture<Void> submitTaskWithFuture(Runnable task) {
        return CompletableFuture.runAsync(() -> runTask(task), threadPool);
    }

    /**
     * 执行任务的核心逻辑，包含代理分配和释放
     */
    private void runTask(Runnable task) {
        SettingsProxy proxy = noProxyMode ? null : acquireProxy();
        String threadName = Thread.currentThread().getName();
        try {
            proxyContext.set(proxy);
            log.info("{} 开始执行任务，使用代理: {}", threadName, proxy != null ? proxy.getIp() + ":" + proxy.getPort() : "无");
            task.run();
            log.debug("{} 任务执行完成", threadName);
        } catch (Exception e) {
            log.error("{} 任务执行失败，代理: {}, 错误: {}", threadName, proxy != null ? proxy.getIp() + ":" + proxy.getPort() : "无", e.getMessage());
        } finally {
            if (!noProxyMode) releaseProxy(proxy);
            proxyContext.remove();
        }
    }

    /**
     * 获取当前线程的代理
     */
    public SettingsProxy getCurrentProxy() {
        return proxyContext.get();
    }

    /**
     * 获取空闲代理，线程安全，仅在有代理时调用
     */
    private SettingsProxy acquireProxy() {
        lock.lock();
        try {
            while (true) {
                for (SettingsProxy proxy : proxies) {
                    if (!proxyStatus.get(proxy.getId())) {
                        proxyStatus.put(proxy.getId(), true);
                        log.debug("分配代理: {}:{} 给线程: {}", proxy.getIp(), proxy.getPort(), Thread.currentThread().getName());
                        return proxy;
                    }
                }
                log.warn("当前无空闲代理，线程 {} 等待中...", Thread.currentThread().getName());
                lock.unlock();
                Thread.sleep(100);
                lock.lock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待代理时被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /**
     * 释放代理，标记为空闲，仅在有代理时调用
     */
    private void releaseProxy(SettingsProxy proxy) {
        lock.lock();
        try {
            proxyStatus.put(proxy.getId(), false);
            log.info("代理释放: {}:{}", proxy.getIp(), proxy.getPort());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 优雅关闭线程池
     */
    public void shutdown() {
        log.info("开始关闭代理池...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                log.warn("线程池未能在60秒内关闭，强制终止");
            } else {
                log.info("代理池成功关闭");
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("关闭代理池时被中断", e);
        }
    }
}