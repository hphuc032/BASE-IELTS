package com.baseielts.platform.content;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogConnectionAdmissionTest {
    @Test
    void rejectsBeforeCheckoutAndReturnsCapacityExactlyOnce() throws Exception {
        var pool = mock(HikariDataSource.class);
        var first = mock(Connection.class);
        var second = mock(Connection.class);
        when(pool.getMaximumPoolSize()).thenReturn(1);
        when(pool.getConnection()).thenReturn(first, second);
        var admission = new CatalogConnectionAdmission(pool);

        var lease = admission.getConnection();
        assertSame(lease, lease.unwrap(Connection.class));
        assertThrows(SQLTransientConnectionException.class, admission::getConnection);
        verify(pool).getConnection();
        lease.close();
        lease.close();
        verify(first).close();
        try (var next = admission.getConnection()) {
            assertThrows(SQLTransientConnectionException.class, admission::getConnection);
        }
        verify(pool, times(2)).getConnection();
        verify(second).close();
    }

    @Test
    void releasesCapacityAfterFailedCheckoutAndAbort() throws Exception {
        var pool = mock(HikariDataSource.class);
        var borrowed = mock(Connection.class);
        var next = mock(Connection.class);
        var failure = new SQLException("fixture checkout failure", "08006");
        when(pool.getMaximumPoolSize()).thenReturn(1);
        when(pool.getConnection()).thenThrow(failure).thenReturn(borrowed, next);
        var admission = new CatalogConnectionAdmission(pool);

        assertSame(failure, assertThrows(SQLException.class, admission::getConnection));
        var lease = admission.getConnection();
        java.util.concurrent.Executor executor = Runnable::run;
        lease.abort(executor);
        verify(borrowed).abort(executor);
        verify(borrowed).close();
        try (var finalLease = admission.getConnection()) {
            assertEquals("08001", assertThrows(SQLTransientConnectionException.class, admission::getConnection).getSQLState());
        }
        verify(next).close();
    }
}
