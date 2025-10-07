package com.tejas.icedtea.util;

import com.tejas.icedtea.IcedTeaMod;
import java.util.concurrent.*;

public class ThreadPoolManager {
    private static ExecutorService executor;
    private static int threadCount;
    
    public static void initialize(int threads) {
        threadCount = Math.max(2, threads);
        executor = new ThreadPoolExecutor(
            threadCount,
            threadCount,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            new ThreadFactory() {
                private int counter = 0;
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("IcedTea-Worker-" + counter++);
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        IcedTeaMod.LOGGER.info("Initialized thread pool with {} threads", threadCount);
    }
    
    public static ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            initialize(Runtime.getRuntime().availableProcessors() - 2);
        }
        return executor;
    }
    
    public static <T> Future<T> submit(Callable<T> task) {
        return getExecutor().submit(task);
    }
    
    public static void execute(Runnable task) {
        getExecutor().execute(task);
    }
    
    public static int getThreadCount() {
        return threadCount;
    }
    
    public static void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            IcedTeaMod.LOGGER.info("Shutting down thread pool...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        IcedTeaMod.LOGGER.error("Thread pool did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}