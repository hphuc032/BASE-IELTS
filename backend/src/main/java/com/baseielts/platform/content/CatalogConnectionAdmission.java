package com.baseielts.platform.content;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransientConnectionException;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Catalog owns each backing pool exclusively and admits at most its configured capacity. */
final class CatalogConnectionAdmission extends DelegatingDataSource {
    private final Semaphore capacity;

    CatalogConnectionAdmission(HikariDataSource source) {
        super(Objects.requireNonNull(source));
        int maximum = source.getMaximumPoolSize();
        if (maximum < 1) {
            throw new IllegalArgumentException("Catalog connection capacity must be positive");
        }
        capacity = new Semaphore(maximum);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!capacity.tryAcquire()) {
            throw new SQLTransientConnectionException("Catalog connection capacity exhausted", "08001");
        }
        Connection connection;
        try {
            connection = super.getConnection();
        } catch (SQLException | RuntimeException | Error failure) {
            capacity.release();
            throw failure;
        }
        var returned = new AtomicBoolean();
        try {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> invoke(connection, returned, (Connection) proxy, method, arguments));
        } catch (RuntimeException | Error failure) {
            try {
                close(connection, returned);
            } catch (SQLException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private Object invoke(Connection connection, AtomicBoolean returned, Connection proxy, Method method, Object[] arguments)
            throws Throwable {
        switch (method.getName()) {
            case "close":
                close(connection, returned);
                return null;
            case "abort":
                try (proxy) {
                    return invokeOn(connection, method, arguments);
                }
            case "isClosed":
                return returned.get() || connection.isClosed();
            case "isWrapperFor":
                return ((Class<?>) arguments[0]).isInstance(proxy);
            case "unwrap":
                return unwrapSelf(proxy, (Class<?>) arguments[0]);
            default:
                return invokeOn(connection, method, arguments);
        }
    }

    private static Object invokeOn(Connection connection, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(connection, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private void close(Connection connection, AtomicBoolean returned) throws SQLException {
        if (returned.compareAndSet(false, true)) {
            try {
                connection.close();
            } finally {
                capacity.release();
            }
        }
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException("Catalog uses only its configured database principal");
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return type.cast(unwrapSelf(this, type));
    }

    private static Object unwrapSelf(Object wrapper, Class<?> type) throws SQLException {
        if (!type.isInstance(wrapper)) {
            throw new SQLException("Catalog JDBC target cannot be unwrapped");
        }
        return wrapper;
    }
}
