package com.neovisionaries.ws.client;

import static org.junit.Assert.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import org.junit.Test;

public class ConnectionAttemptLimitTest {
    @Test public void oneLimitStartsOneRacerForManyResolvedAddresses() throws Exception {
        CountingFailureFactory factory = new CountingFailureFactory();
        InetAddress[] resolved = new InetAddress[128];
        for (int i = 0; i < resolved.length; i++) resolved[i] = InetAddress.getByAddress(new byte[]{127, 0, 0, (byte)(i + 1)});
        SocketInitiator initiator = new SocketInitiator(factory, new Address("synthetic.invalid", 1), 100, null, DualStackMode.BOTH, 0, 1);
        long before = ManagementFactory.getThreadMXBean().getTotalStartedThreadCount();
        assertThrows(IOException.class, () -> initiator.establish(resolved));
        assertEquals(1, factory.created.get());
        assertEquals(1L, ManagementFactory.getThreadMXBean().getTotalStartedThreadCount() - before);
    }

    @Test public void oldConstructorAndZeroRetainAllEligibleAddressAttempts() throws Exception {
        InetAddress[] resolved = {v4(), v4(), v4()};
        CountingFailureFactory legacy = new CountingFailureFactory();
        assertThrows(IOException.class, () -> new SocketInitiator(legacy, new Address("synthetic.invalid", 1), 100, null, DualStackMode.BOTH, 0).establish(resolved));
        assertEquals(3, legacy.created.get());
        CountingFailureFactory zero = new CountingFailureFactory();
        assertThrows(IOException.class, () -> new SocketInitiator(zero, new Address("synthetic.invalid", 1), 100, null, DualStackMode.BOTH, 0, 0).establish(resolved));
        assertEquals(3, zero.created.get());
    }

    @Test public void capCountsEligibleAddressesAfterFamilyFiltering() throws Exception {
        CountingFailureFactory factory = new CountingFailureFactory();
        InetAddress[] resolved = {InetAddress.getByAddress(new byte[16]), v4(), v4()};
        assertThrows(IOException.class, () -> new SocketInitiator(factory, new Address("synthetic.invalid", 1), 100, null, DualStackMode.IPV4_ONLY, 0, 1).establish(resolved));
        assertEquals(1, factory.created.get());
    }

    @Test public void factoryDefaultCopyAndInvalidConfigurationKeepExactValues() {
        WebSocketFactory factory = new WebSocketFactory();
        assertEquals(0, factory.getMaximumConnectionAttempts());
        assertSame(factory, factory.setMaximumConnectionAttempts(1));
        assertEquals(1, new WebSocketFactory(factory).getMaximumConnectionAttempts());
        assertThrows(IllegalArgumentException.class, () -> factory.setMaximumConnectionAttempts(-1));
        assertEquals(1, factory.getMaximumConnectionAttempts());
        assertThrows(IllegalArgumentException.class, () -> new SocketInitiator(new CountingFailureFactory(), new Address("synthetic.invalid", 1), 100, null, DualStackMode.BOTH, 0, -1));
    }

    @Test public void socketAndRecreationCaptureLimitForDirectAndProxyConnectors() throws Exception {
        WebSocketFactory factory = new WebSocketFactory().setMaximumConnectionAttempts(1);
        WebSocket socket = factory.createSocket("ws://synthetic.invalid/");
        assertEquals(1, connectorLimit(socket));
        assertEquals(1, connectorLimit(socket.recreate()));
        factory.getProxySettings().setHost("proxy.invalid");
        assertEquals(1, connectorLimit(factory.createSocket("wss://synthetic.invalid/")));
    }

    private static int connectorLimit(WebSocket socket) throws Exception {
        java.lang.reflect.Field connector = WebSocket.class.getDeclaredField("mSocketConnector"); connector.setAccessible(true);
        java.lang.reflect.Field limit = SocketConnector.class.getDeclaredField("mMaximumConnectionAttempts"); limit.setAccessible(true);
        return limit.getInt(connector.get(socket));
    }

    private static InetAddress v4() throws Exception { return InetAddress.getByAddress(new byte[]{127, 0, 0, 1}); }
    private static class CountingFailureFactory extends SocketFactory {
        final AtomicInteger created = new AtomicInteger();
        @Override public Socket createSocket() throws IOException { created.incrementAndGet(); throw new IOException("Synthetic failure"); }
        @Override public Socket createSocket(String h, int p) throws IOException { return createSocket(); }
        @Override public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException { return createSocket(); }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException { return createSocket(); }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException { return createSocket(); }
    }
}
