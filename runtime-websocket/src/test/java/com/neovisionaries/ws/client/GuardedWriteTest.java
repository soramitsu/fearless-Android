package com.neovisionaries.ws.client;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class GuardedWriteTest {
    @Test public void revokedWhileQueuedNeverReachesOutput() throws Exception {
        Harness h = new Harness();
        AtomicBoolean allowed = new AtomicBoolean(true);
        h.send(write -> { if (!allowed.get()) throw new IllegalStateException("revoked"); write.write(); });
        allowed.set(false);
        h.start(); h.awaitFailure(); h.stop();
        assertEquals(0, h.output.attempts.get()); assertFalse(h.failures.get(0).mayHaveBeenWritten());
    }

    @Test public void expiryAfterFramePreparationAndListenerIsCheckedBeforeOutput() throws Exception {
        Harness h = new Harness();
        AtomicLong clock = new AtomicLong(0);
        h.recreateWriter();
        h.socket.addListener(new WebSocketAdapter() {
            @Override public void onSendingFrame(WebSocket socket, WebSocketFrame frame) {
                assertFalse(frame.getRsv1()); clock.set(901);
            }
        });
        h.send(write -> { if (clock.get() >= 900) throw new IllegalStateException("expired"); write.write(); });
        h.start(); h.awaitFailure(); h.stop();
        assertEquals(0, h.output.attempts.get()); assertEquals(1, h.failures.size());
    }

    @Test public void guardedWriteNeverAdvancesNegotiatedCompressionButLegacyStillWorks() throws Exception {
        Harness h = new Harness(); AtomicInteger dictionary = new AtomicInteger();
        h.socket.compression = new PerMessageCompressionExtension("stateful-test") {
            @Override protected byte[] compress(byte[] plain) { dictionary.incrementAndGet(); return plain; }
            @Override protected byte[] decompress(byte[] compressed) { return compressed; }
        };
        h.recreateWriter(); AtomicInteger authorized = new AtomicInteger();
        h.send(write -> { authorized.incrementAndGet(); write.write(); });
        h.start(); h.awaitFailure();
        assertEquals(0, dictionary.get()); assertEquals(0, authorized.get()); assertEquals(0, h.output.attempts.get());
        h.socket.sendText("legacy"); assertTrue(h.output.written.await(3, TimeUnit.SECONDS)); h.stop();
        assertEquals(1, dictionary.get()); assertEquals(1, h.output.attempts.get());
        assertFalse(h.failures.get(0).mayHaveBeenWritten());
    }

    @Test public void retainedActionCannotWriteFromAnotherThread() throws Exception {
        Harness h = new Harness(); AtomicBoolean rejected = new AtomicBoolean();
        h.send(write -> {
            Thread other = new Thread(() -> { try { write.write(); } catch (Exception expected) { rejected.set(true); } });
            other.start(); other.join(3000);
        });
        h.start(); h.awaitFailure(); h.stop();
        assertTrue(rejected.get()); assertEquals(0, h.output.attempts.get());
    }

    @Test public void cancelledQueueCannotSendAndNotifiesOnlyOnce() throws Exception {
        Harness h = new Harness();
        GuardedWriteHandle handle = h.send(WebSocketWriteGuard.WriteAction::write);
        assertTrue(handle.cancel()); assertFalse(handle.cancel());
        h.start(); h.awaitFailure(); h.stop();
        assertEquals(0, h.output.attempts.get()); assertEquals(1, h.failures.size());
    }

    @Test public void partialWriteHasUnknownOutcomeAndRemainingGuardedQueueDoesNotDrain() throws Exception {
        Harness h = new Harness(); h.output.failWrite = true;
        h.send(WebSocketWriteGuard.WriteAction::write);
        h.send(WebSocketWriteGuard.WriteAction::write);
        h.start(); h.writer.join(3000);
        assertFalse(h.writer.isAlive());
        assertEquals(1, h.output.attempts.get()); assertEquals(2, h.failures.size());
        assertTrue(h.failures.get(0).mayHaveBeenWritten());
        assertFalse(h.failures.get(1).mayHaveBeenWritten());
        int accepted = h.output.bytes.size();
        h.socket.output.flush();
        assertEquals(accepted, h.output.bytes.size()); assertEquals(1, h.output.attempts.get());
    }

    @Test public void flushFailureAfterAcceptedWriteIsUnknownAndNeverRetried() throws Exception {
        Harness h = new Harness(); h.output.failFlushAfterWrite = true;
        h.send(WebSocketWriteGuard.WriteAction::write);
        h.start(); h.writer.join(3000);
        assertFalse(h.writer.isAlive()); assertEquals(1, h.output.attempts.get());
        assertEquals(1, h.failures.size()); assertTrue(h.failures.get(0).mayHaveBeenWritten());
    }

    @Test public void stopWhileWaitingForAuthorityCancelsDequeuedFrameWithoutLockInversion() throws Exception {
        Harness h = new Harness(); Object authority = new Object();
        CountDownLatch waiting = new CountDownLatch(1);
        synchronized (authority) {
            h.send(write -> { waiting.countDown(); synchronized (authority) { write.write(); } });
            h.start(); assertTrue(waiting.await(3, TimeUnit.SECONDS));
            h.writer.requestStop(); h.awaitFailure();
        }
        h.stop(); assertEquals(0, h.output.attempts.get()); assertEquals(1, h.failures.size());
    }

    @Test public void deniedCallbackRunsOutsideAuthorityAndWriterLocksAndCanQueueLegacyRead() throws Exception {
        Harness h = new Harness(); Object authority = new Object(); AtomicBoolean outside = new AtomicBoolean();
        h.socket.sendTextGuarded("mutation", write -> {
            synchronized (authority) { throw new IllegalStateException("denied"); }
        }, failure -> {
            outside.set(!Thread.holdsLock(authority) && !Thread.holdsLock(h.writer));
            h.socket.sendText("legacy read"); h.failed.countDown();
        });
        h.start(); h.awaitFailure(); assertTrue(h.output.written.await(3, TimeUnit.SECONDS)); h.stop();
        assertTrue(outside.get()); assertEquals(1, h.output.attempts.get());
    }

    @Test public void listenerCannotChangeFrozenAuthorizedMessage() throws Exception {
        Harness h = new Harness();
        h.socket.addListener(new WebSocketAdapter() {
            @Override public void onSendingFrame(WebSocket socket, WebSocketFrame frame) { frame.setPayload("substituted"); }
        });
        h.send(WebSocketWriteGuard.WriteAction::write);
        h.start(); assertTrue(h.output.written.await(3, TimeUnit.SECONDS)); h.stop();
        assertEquals(Harness.MESSAGE, unmask(h.output.bytes.toByteArray()));
    }

    @Test public void duplicateGuardInvocationWritesOnlyOnceAndReportsUnknownOutcome() throws Exception {
        Harness h = new Harness();
        h.send(write -> { write.write(); write.write(); });
        h.start(); h.awaitFailure(); h.stop();
        assertEquals(1, h.output.attempts.get()); assertTrue(h.failures.get(0).mayHaveBeenWritten());
    }

    @Test public void guardThatDoesNotInvokeHandoffFailsClosed() throws Exception {
        Harness h = new Harness(); h.send(write -> {});
        h.start(); h.awaitFailure(); h.stop();
        assertEquals(0, h.output.attempts.get()); assertEquals(1, h.failures.size());
    }

    @Test public void rejectedQueueAndDisconnectedSocketFailWithoutSending() throws Exception {
        Harness h = new Harness(); h.socket.setFrameQueueSize(1);
        h.send(WebSocketWriteGuard.WriteAction::write);
        GuardedWriteHandle rejected = h.send(WebSocketWriteGuard.WriteAction::write);
        assertFalse(rejected.cancel()); assertEquals(1, h.failures.size());
        h.socket.getStateManager().setState(WebSocketState.CLOSED);
        h.send(WebSocketWriteGuard.WriteAction::write); assertEquals(2, h.failures.size());
        h.writer.requestStop(); assertEquals(3, h.failures.size()); assertEquals(0, h.output.attempts.get());
    }

    @Test public void revocationCannotInterleaveWithCommittedOutputAndLaterMessagesAreDenied() throws Exception {
        Harness h = new Harness(); Object authority = new Object(); AtomicBoolean allowed = new AtomicBoolean(true);
        h.output.releaseWrite = new CountDownLatch(1);
        WebSocketWriteGuard guard = write -> { synchronized (authority) {
            if (!allowed.get()) throw new IllegalStateException("revoked"); write.write();
        }};
        h.send(guard); h.start(); assertTrue(h.output.written.await(3, TimeUnit.SECONDS));
        CountDownLatch revoking = new CountDownLatch(1); CountDownLatch revoked = new CountDownLatch(1);
        Thread revoker = new Thread(() -> { revoking.countDown(); synchronized (authority) { allowed.set(false); } revoked.countDown(); });
        revoker.start(); assertTrue(revoking.await(3, TimeUnit.SECONDS));
        assertFalse(revoked.await(50, TimeUnit.MILLISECONDS));
        h.output.releaseWrite.countDown(); assertTrue(revoked.await(3, TimeUnit.SECONDS));
        h.send(guard); h.awaitFailure(); h.stop(); revoker.join(3000);
        assertEquals(1, h.output.attempts.get()); assertEquals(1, h.failures.size());
    }

    @Test public void delayFlushingPrecedingOutputCannotCarryStaleAuthorization() throws Exception {
        Harness h = new Harness(); AtomicLong clock = new AtomicLong(0);
        h.output.releasePreFlush = new CountDownLatch(1);
        h.send(write -> { if (clock.get() >= 900) throw new IllegalStateException("expired"); write.write(); });
        h.start(); assertTrue(h.output.preFlushEntered.await(3, TimeUnit.SECONDS));
        clock.set(900); h.output.releasePreFlush.countDown();
        h.awaitFailure(); h.stop(); assertEquals(0, h.output.attempts.get());
    }

    private static String unmask(byte[] bytes) throws Exception {
        int length = bytes[1] & 127, offset = 2;
        if (length == 126) { length = (bytes[2] & 255) * 256 + (bytes[3] & 255); offset = 4; }
        byte[] payload = new byte[length];
        for (int i = 0; i < length; i++) payload[i] = (byte) (bytes[offset + 4 + i] ^ bytes[offset + i % 4]);
        return new String(payload, "UTF-8");
    }

    private static class Harness {
        static final String MESSAGE = "mutation:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        final RecordingOutput output = new RecordingOutput();
        final TestSocket socket = new TestSocket(output);
        final List<GuardedWriteFailure> failures = Collections.synchronizedList(new ArrayList<GuardedWriteFailure>());
        final CountDownLatch failed = new CountDownLatch(1);
        WritingThread writer;
        Harness() throws Exception { recreateWriter(); }
        void recreateWriter() throws Exception {
            writer = new WritingThread(socket);
            Field field = WebSocket.class.getDeclaredField("mWritingThread"); field.setAccessible(true); field.set(socket, writer);
        }
        GuardedWriteHandle send(WebSocketWriteGuard guard) { return socket.sendTextGuarded(MESSAGE, guard, failure -> { failures.add(failure); failed.countDown(); }); }
        void start() { writer.start(); }
        void awaitFailure() throws Exception { assertTrue(failed.await(3, TimeUnit.SECONDS)); }
        void stop() throws Exception { writer.requestStop(); writer.join(3000); assertFalse(writer.isAlive()); }
    }

    private static class TestSocket extends WebSocket {
        final WebSocketOutputStream output;
        PerMessageCompressionExtension compression;
        TestSocket(OutputStream output) {
            super(new WebSocketFactory(), false, null, "test.invalid", "/", null);
            this.output = new WebSocketOutputStream(output); getStateManager().setState(WebSocketState.OPEN);
        }
        @Override WebSocketOutputStream getOutput() { return output; }
        @Override PerMessageCompressionExtension getPerMessageCompressionExtension() { return compression; }
        @Override void onWritingThreadStarted() {}
        @Override void onWritingThreadFinished(WebSocketFrame frame) {}
    }

    private static class RecordingOutput extends OutputStream {
        final AtomicInteger attempts = new AtomicInteger();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final CountDownLatch written = new CountDownLatch(1);
        volatile CountDownLatch releaseWrite;
        volatile CountDownLatch releasePreFlush;
        final CountDownLatch preFlushEntered = new CountDownLatch(1);
        final AtomicBoolean preFlushStarted = new AtomicBoolean();
        boolean failWrite;
        boolean failFlushAfterWrite;
        @Override public void write(int b) { bytes.write(b); }
        @Override public void write(byte[] data) throws IOException { write(data, 0, data.length); }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            attempts.incrementAndGet(); written.countDown();
            if (releaseWrite != null) try { if (!releaseWrite.await(3, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
            if (failWrite) { bytes.write(data[offset]); throw new IOException("partial write"); }
            bytes.write(data, offset, length);
        }
        @Override public void flush() throws IOException {
            if (releasePreFlush != null && preFlushStarted.compareAndSet(false, true)) {
                preFlushEntered.countDown();
                try { if (!releasePreFlush.await(3, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
            }
            if (failFlushAfterWrite && attempts.get() > 0) throw new IOException("flush");
        }
    }
}
