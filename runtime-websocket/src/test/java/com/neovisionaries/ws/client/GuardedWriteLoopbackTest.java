package com.neovisionaries.ws.client;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class GuardedWriteLoopbackTest {
    @Test public void authorizedBytesReachLoopbackSocketExactlyOnce() throws Exception {
        try (Peer peer = new Peer()) {
            WebSocket socket = new WebSocketFactory().createSocket(peer.url(), 3000).connect();
            CountDownLatch sent = new CountDownLatch(1);
            AtomicBoolean failed = new AtomicBoolean();
            socket.addListener(new WebSocketAdapter() {
                @Override public void onFrameSent(WebSocket ws, WebSocketFrame frame) {
                    if (frame.isTextFrame()) sent.countDown();
                }
            });
            socket.sendTextGuarded("authorized", WebSocketWriteGuard.WriteAction::write, failure -> failed.set(true));
            assertEquals("authorized", peer.message.get(3, TimeUnit.SECONDS));
            assertTrue(sent.await(3, TimeUnit.SECONDS)); assertFalse(failed.get());
            socket.disconnect();
        }
    }

    @Test public void revocationDuringPreparationSendsOnlyTheFollowingLegacyMessage() throws Exception {
        try (Peer peer = new Peer()) {
            WebSocket socket = new WebSocketFactory().createSocket(peer.url(), 3000).connect();
            CountDownLatch preparing = new CountDownLatch(1), release = new CountDownLatch(1), denied = new CountDownLatch(1);
            AtomicBoolean allowed = new AtomicBoolean(true);
            socket.addListener(new WebSocketAdapter() {
                @Override public void onSendingFrame(WebSocket ws, WebSocketFrame frame) throws Exception {
                    if ("mutation".equals(frame.getPayloadText())) {
                        preparing.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS));
                    }
                }
            });
            socket.sendTextGuarded("mutation", write -> {
                if (!allowed.get()) throw new IllegalStateException("revoked"); write.write();
            }, failure -> denied.countDown());
            assertTrue(preparing.await(3, TimeUnit.SECONDS)); allowed.set(false); release.countDown();
            assertTrue(denied.await(3, TimeUnit.SECONDS)); socket.sendText("legacy");
            assertEquals("legacy", peer.message.get(3, TimeUnit.SECONDS));
            socket.disconnect();
        }
    }

    private static final class Peer implements AutoCloseable {
        final ServerSocket server;
        final CompletableFuture<String> message = new CompletableFuture<String>();
        volatile Socket accepted;
        final Thread reader;
        Peer() throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); server.setSoTimeout(3000);
            reader = new Thread(() -> {
                try (Socket connection = server.accept()) {
                    accepted = connection; connection.setSoTimeout(3000);
                    InputStream input = connection.getInputStream();
                    BufferedReader headers = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII));
                    String key = null, line;
                    while ((line = headers.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-key:")) key = line.substring(line.indexOf(':') + 1).trim();
                    }
                    if (key == null) throw new IllegalStateException("Missing WebSocket key");
                    String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                    connection.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    connection.getOutputStream().flush();
                    int opcode = input.read(), length = input.read();
                    if (opcode != 0x81 || (length & 0x80) == 0 || (length & 127) > 125) throw new IllegalStateException("Unexpected frame");
                    byte[] mask = exact(input, 4), payload = exact(input, length & 127);
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                    message.complete(new String(payload, StandardCharsets.UTF_8));
                    // Keep the peer alive until the client closes, preventing close/error races
                    // from being mistaken for failure of the successful output operation.
                    while (input.read() != -1) {}
                } catch (Throwable failure) { message.completeExceptionally(failure); }
            });
            reader.start();
        }
        String url() { return "ws://localhost:" + server.getLocalPort(); }
        static byte[] exact(InputStream input, int length) throws Exception {
            byte[] bytes = new byte[length]; int read = 0;
            while (read < length) { int count = input.read(bytes, read, length - read); if (count < 0) throw new IllegalStateException("Truncated frame"); read += count; }
            return bytes;
        }
        @Override public void close() throws Exception {
            if (accepted != null) accepted.close(); server.close(); reader.join(4000); assertFalse(reader.isAlive());
        }
    }
}
