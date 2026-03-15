package com.daspos.shared.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DbExecutor {
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private DbExecutor() {}

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
}
