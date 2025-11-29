package com.yliahs.client.temp2.concurrent;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 简单的并发连接配额控制。
 */
public class ConnectionLimiter {

    private final Semaphore semaphore;

    public ConnectionLimiter(int maxConcurrentConnections) {
        if (maxConcurrentConnections <= 0) {
            throw new IllegalArgumentException("maxConcurrentConnections <= 0");
        }
        this.semaphore = new Semaphore(maxConcurrentConnections);
    }

    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public boolean tryAcquire(long timeoutMillis) throws InterruptedException {
        return semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void release() {
        semaphore.release();
    }
}

