package com.baseielts.platform.identity;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
final class VerificationConnectionAdmission {
    private final DataSource pool;
    private final ThreadPoolExecutor leases = new ThreadPoolExecutor(
        0, 16, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
        Thread.ofPlatform().daemon().name("verification-db-lease-", 0).factory());

    VerificationConnectionAdmission(DataSource pool) {
        this.pool = pool;
    }

    Connection acquire(long deadline) throws SQLException {
        CompletableFuture<Connection> handoff = new CompletableFuture<>();
        try {
            leases.execute(() -> {
                try {
                    if (handoff.isDone()) return;
                    Connection connection = pool.getConnection();
                    if (!handoff.complete(connection)) connection.close();
                } catch (SQLException failure) {
                    handoff.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException unavailable) {
            throw new SQLException("Verification connection unavailable", unavailable);
        }
        try {
            return handoff.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException failure) {
            reclaim(handoff);
            throw new SQLException("Verification connection deadline exceeded", failure);
        } catch (InterruptedException failure) {
            reclaim(handoff);
            Thread.currentThread().interrupt();
            throw new SQLException("Verification connection interrupted", failure);
        } catch (ExecutionException failure) {
            throw new SQLException("Verification connection unavailable", failure);
        }
    }

    private static void reclaim(CompletableFuture<Connection> handoff) {
        if (!handoff.cancel(false)) {
            handoff.thenAccept(connection -> {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // The pool evicts failed leases. No transaction began on this connection.
                }
            });
        }
    }

    @PreDestroy
    void shutdown() {
        leases.shutdownNow();
    }
}
