package com.baseielts.platform.identity;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.net.SocketFactory;

/** One physical test connection whose next inbound bytes can be lost at the JDBC boundary. */
public final class LostCommitReplySocketFactory extends SocketFactory {
    private static final ThreadLocal<LostCommitReplySocketFactory> NEXT = new ThreadLocal<>();
    private boolean armed;
    private boolean replyDropped;
    private long delayMillis;

    public LostCommitReplySocketFactory() {
    }

    static LostCommitReplySocketFactory fixture() {
        return new LostCommitReplySocketFactory();
    }

    Connection connect(String url, String user, String password) throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("socketFactory", getClass().getName());
        NEXT.set(this);
        try {
            return DriverManager.getConnection(url, properties);
        } finally {
            NEXT.remove();
        }
    }

    void arm() {
        arm(0);
    }

    void arm(long delayMillis) {
        this.delayMillis = delayMillis;
        armed = true;
    }

    boolean replyDropped() {
        return replyDropped;
    }

    @Override
    public Socket createSocket() {
        var fixture = NEXT.get();
        if (fixture == null) throw new IllegalStateException("Missing JDBC test fault fixture");
        return new Socket() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FilterInputStream(super.getInputStream()) {
                    @Override public int read() throws IOException {
                        return observe(in.read(), 0);
                    }

                    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                        return observe(in.read(bytes, offset, length), -1);
                    }

                    private int observe(int read, int empty) throws IOException {
                        if (!fixture.armed || read <= empty) return read;
                        fixture.armed = false;
                        fixture.replyDropped = true;
                        try {
                            if (fixture.delayMillis > 0) Thread.sleep(fixture.delayMillis);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        closeTransport();
                        throw new java.net.SocketTimeoutException("Fixture suppressed PostgreSQL commit acknowledgement");
                    }
                };
            }

            private void closeTransport() throws IOException {
                super.close();
            }
        };
    }

    private Socket connectSocket(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) socket.bind(local);
            socket.connect(remote);
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    @Override public Socket createSocket(String host, int port) throws IOException {
        return connectSocket(new InetSocketAddress(host, port), null);
    }

    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return connectSocket(new InetSocketAddress(host, port), null);
    }

    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return connectSocket(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }

    @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
        return connectSocket(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
}
