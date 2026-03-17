package com.daspos.shared.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DbExecutor {
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DbExecutor() {}

    public interface SuccessCallback<T> {
        void onSuccess(T result);
    }

    public interface ErrorCallback {
        void onError(Throwable throwable);
    }

    public static <T> T runBlocking(Callable<T> task) {
        Future<T> future = IO.submit(task);
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
        IO.execute(task);
    }

    public static <T> void runAsync(
            final Callable<T> task,
            final SuccessCallback<T> onSuccess,
            final ErrorCallback onError
    ) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
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
                }
            }
        });
    }
}
