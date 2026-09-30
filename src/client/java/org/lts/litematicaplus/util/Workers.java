package org.lts.litematicaplus.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Workers {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static volatile ThreadPoolExecutor mesh;
    private static volatile ExecutorService io;
    private static volatile ExecutorService writer;

    private Workers() {
    }

    public static ThreadPoolExecutor mesh(int threads) {
        ThreadPoolExecutor current = mesh;
        if (current == null || current.isShutdown()) {
            synchronized (Workers.class) {
                current = mesh;
                if (current == null || current.isShutdown()) {
                    current = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(), factory("LitematicaPlus-Mesh", Thread.NORM_PRIORITY - 2));
                    mesh = current;
                }
            }
        }
        if (current.getCorePoolSize() != threads) {
            if (threads > current.getMaximumPoolSize()) {
                current.setMaximumPoolSize(threads);
                current.setCorePoolSize(threads);
            } else {
                current.setCorePoolSize(threads);
                current.setMaximumPoolSize(threads);
            }
        }
        return current;
    }

    public static ExecutorService io() {
        ExecutorService current = io;
        if (current == null || current.isShutdown()) {
            synchronized (Workers.class) {
                current = io;
                if (current == null || current.isShutdown()) {
                    current = Executors.newFixedThreadPool(2, factory("LitematicaPlus-IO", Thread.NORM_PRIORITY - 1));
                    io = current;
                }
            }
        }
        return current;
    }

    public static ExecutorService writer() {
        ExecutorService current = writer;
        if (current == null || current.isShutdown()) {
            synchronized (Workers.class) {
                current = writer;
                if (current == null || current.isShutdown()) {
                    current = Executors.newSingleThreadExecutor(factory("LitematicaPlus-Writer", Thread.NORM_PRIORITY - 1));
                    writer = current;
                }
            }
        }
        return current;
    }

    public static void shutdown() {
        synchronized (Workers.class) {
            if (writer != null) {
                writer.shutdown();
                try {
                    writer.awaitTermination(5L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                writer = null;
            }
            if (mesh != null) {
                mesh.shutdownNow();
                mesh = null;
            }
            if (io != null) {
                io.shutdownNow();
                io = null;
            }
        }
    }

    private static ThreadFactory factory(String prefix, int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + COUNTER.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        };
    }
}
