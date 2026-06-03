package druid.elf.tool.service.proxy;

import druid.elf.tool.entity.SettingsProxy;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


@Slf4j
public class ProxyPoolManager {
    private final List<SettingsProxy> proxies;
    private final ExecutorService threadPool;
    private final Map<String, Boolean> proxyStatus;
    private final ReentrantLock lock = new ReentrantLock();
    private final ThreadLocal<SettingsProxy> proxyContext = new ThreadLocal<>();
    private final boolean noProxyMode;


    public ProxyPoolManager(List<SettingsProxy> proxies) {
        this.proxies = proxies != null ? proxies : List.of();
        this.proxyStatus = new ConcurrentHashMap<>();
        this.noProxyMode = this.proxies.isEmpty();

        if (!noProxyMode) {
            this.proxies.forEach(proxy -> proxyStatus.put(proxy.getId(), false));
        }


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
        log.info("Proxy pool initialized, proxies: {}, pool size: {}", this.proxies.size(), poolSize);
    }


    public CompletableFuture<Void> submitTaskWithFuture(Runnable task) {
        return CompletableFuture.runAsync(() -> runTask(task), threadPool);
    }


    private void runTask(Runnable task) {
        SettingsProxy proxy = noProxyMode ? null : acquireProxy();
        String threadName = Thread.currentThread().getName();
        try {
            proxyContext.set(proxy);
            log.info("{} Task started, using proxy: {}", threadName, proxy != null ? proxy.getIp() + ":" + proxy.getPort() : "none");
            task.run();
            log.debug("{} Task completed", threadName);
        } catch (Exception e) {
            log.error("{} Task failed, proxy: {}, error: {}", threadName, proxy != null ? proxy.getIp() + ":" + proxy.getPort() : "none", e.getMessage());
        } finally {
            if (!noProxyMode) releaseProxy(proxy);
            proxyContext.remove();
        }
    }


    public SettingsProxy getCurrentProxy() {
        return proxyContext.get();
    }


    private SettingsProxy acquireProxy() {
        lock.lock();
        try {
            while (true) {
                for (SettingsProxy proxy : proxies) {
                    if (!proxyStatus.get(proxy.getId())) {
                        proxyStatus.put(proxy.getId(), true);
                        log.debug("Assigning proxy: {}:{} to thread {}", proxy.getIp(), proxy.getPort(), Thread.currentThread().getName());
                        return proxy;
                    }
                }
                log.warn("No available proxies, thread {} waiting...", Thread.currentThread().getName());
                lock.unlock();
                Thread.sleep(100);
                lock.lock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for proxy");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }


    private void releaseProxy(SettingsProxy proxy) {
        lock.lock();
        try {
            proxyStatus.put(proxy.getId(), false);
            log.info("Proxy released: {}:{}", proxy.getIp(), proxy.getPort());
        } finally {
            lock.unlock();
        }
    }


    public void shutdown() {
        log.info("Shutting down proxy pool...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                log.warn("Thread pool failed to shutdown within 60s, forced termination");
            } else {
                log.info("Proxy pool shutdown successfully");
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("Proxy pool shutdown interrupted", e);
        }
    }
}