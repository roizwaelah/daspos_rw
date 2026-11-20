package com.daspos.shared.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DbExecutor {
    // Separated executors based on workload types
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    
    // DB & Local IO: Fast, short-lived tasks
    private static final int DB_POOL_SIZE = Math.max(2, Math.min(CORES, 4));
    private static final ExecutorService DB_IO = Executors.newFixedThreadPool(DB_POOL_SIZE);
    
    // Network & Sync: Long-lived I/O, waiting for server
    private static final int NETWORK_POOL_SIZE = Math.max(2, Math.min(CORES * 2, 6));
    private static final ExecutorService NETWORK_IO = Executors.newFixedThreadPool(NETWORK_POOL_SIZE);
    
    // Heavy Computation & Export: CPU bound, Apache POI
    private static final ExecutorService EXPORT_COMPUTE = Executors.newFixedThreadPool(Math.max(1, CORES - 1));

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadLocal<Boolean> ON_DB_THREAD = new ThreadLocal<>();

    private DbExecutor() {}

    public static ExecutorService getDbIo() { return DB_IO; }
    public static ExecutorService getNetworkIo() { return NETWORK_IO; }
    public static ExecutorService getExportCompute() { return EXPORT_COMPUTE; }

    public interface SuccessCallback<T> {
        void onSuccess(T result);
    }

    public interface ErrorCallback {
        void onError(Throwable throwable);
    }

    public static <T> T runBlocking(Callable<T> task) {
        // Nested runBlocking inside a pool task would occupy a second pool slot
        // and can deadlock the fixed pool; run inline on IO threads instead.
        if (Boolean.TRUE.equals(ON_DB_THREAD.get())) {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Future<T> future = DB_IO.submit(new Callable<T>() {
            @Override public T call() throws Exception {
                ON_DB_THREAD.set(Boolean.TRUE);
                try {
                    return task.call();
                } finally {
                    ON_DB_THREAD.remove();
                }
            }
        });
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void runBlocking(Runnable task) {
        runBlocking(new Callable<Void>() {
            @Override public Void call() {
                task.run();
                return null;
            }
        });
    }

    public static void runAsync(final Runnable task) {
        DB_IO.execute(wrap(task, true));
    }

    public static void runNetworkAsync(final Runnable task) {
        // Network threads must NOT be marked as DB threads: runBlocking calls
        // inside them have to hop to DB_IO, otherwise Room/SQLite work runs
        // concurrently on network threads and convoy-locks the database.
        NETWORK_IO.execute(wrap(task, false));
    }

    public static void runExportAsync(final Runnable task) {
        EXPORT_COMPUTE.execute(wrap(task, false));
    }

    public static <T> void runAsync(
            final Callable<T> task,
            final SuccessCallback<T> onSuccess,
            final ErrorCallback onError
    ) {
        DB_IO.execute(new Runnable() {
            @Override
            public void run() {
                ON_DB_THREAD.set(Boolean.TRUE);
                try {
                    final T result = task.call();
                    if (onSuccess != null) {
                        MAIN.post(new Runnable() {
                            @Override public void run() {
                                onSuccess.onSuccess(result);
                            }
                        });
                    }
                } catch (final Throwable throwable) {
                    if (onError != null) {
                        MAIN.post(new Runnable() {
                            @Override public void run() {
                                onError.onError(throwable);
                            }
                        });
                    }
                } finally {
                    ON_DB_THREAD.remove();
                }
            }
        });
    }

    private static Runnable wrap(final Runnable task, final boolean markDbThread) {
        return new Runnable() {
            @Override
            public void run() {
                if (markDbThread) ON_DB_THREAD.set(Boolean.TRUE);
                try {
                    task.run();
                } finally {
                    if (markDbThread) ON_DB_THREAD.remove();
                }
            }
        };
    }
}
