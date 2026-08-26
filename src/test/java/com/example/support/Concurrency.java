package com.example.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * 高并发测试工具：用 CountDownLatch 让所有线程「同时开始」，制造真正的并发。
 */
public final class Concurrency {

    private Concurrency() {
    }

    /**
     * 用 threads 个线程并发执行 action，返回每个线程的结果（按线程索引顺序）。
     *
     * @param threads 并发线程数
     * @param action  接收线程索引 i，返回该线程的执行结果
     */
    public static <T> List<T> run(int threads, IntFunction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Callable<T> task = () -> {
                ready.countDown();   // 本线程已就绪
                start.await();       // 阻塞，等所有线程就绪
                return action.apply(idx);
            };
            futures.add(pool.submit(task));
        }

        ready.await();      // 等全部线程就绪
        start.countDown();  // 同时放行 → 真正并发
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }
}
