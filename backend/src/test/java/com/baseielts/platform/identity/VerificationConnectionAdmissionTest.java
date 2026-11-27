package com.baseielts.platform.identity;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationConnectionAdmissionTest {
    @Test
    void timedOutAcquisitionClosesLateLeaseWithoutStartingTransaction() throws Exception {
        DataSource pool = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        when(pool.getConnection()).thenAnswer(call -> {
            started.countDown();
            release.await();
            return connection;
        });
        doAnswer(call -> { closed.countDown(); return null; }).when(connection).close();
        var admission = new VerificationConnectionAdmission(pool);
        try {
            assertThrows(SQLException.class,
                () -> admission.acquire(System.nanoTime() + Duration.ofMillis(150).toNanos()));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            verify(connection, never()).setAutoCommit(false);
        } finally {
            release.countDown();
            admission.shutdown();
        }
    }
}
