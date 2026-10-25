package com.neovisionaries.ws.client;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Incoming framing and cumulative parser budgets are enforced before payload reads. */
public class IncomingResponseLimitsTest {
    @Test public void extendedLengthUsesAllSixtyThreeBitsWithoutDrainingOversizedPayload() throws Exception {
        for (long length : new long[]{2147483648L, 4294967297L, Long.MAX_VALUE}) {
            CountingInput input = new CountingInput(extended(length, new byte[]{65}));
            WebSocketException error = assertThrows(WebSocketException.class,
                () -> new WebSocketInputStream(input).readFrame());
            assertEquals(WebSocketError.TOO_LONG_PAYLOAD, error.getError());
            assertEquals(10, input.consumed);
            assertEquals(0, input.skipped);
        }
    }

    @Test public void validExtendedLengthKeepsExactPayload() throws Exception {
        byte[] payload = new byte[65536];
        payload[65535] = 73;
        assertArrayEquals(payload, new WebSocketInputStream(new ByteArrayInputStream(extended(65536, payload))).readFrame().getPayload());
    }

    @Test public void signedExtendedLengthIsRejectedBeforePayload() throws Exception {
        CountingInput input = new CountingInput(extended(Long.MIN_VALUE, new byte[]{65}));
        WebSocketException error = assertThrows(WebSocketException.class,
            () -> new WebSocketInputStream(input, 512, 1024).readFrame());
        assertEquals(WebSocketError.INVALID_PAYLOAD_LENGTH, error.getError());
        assertEquals(10, input.consumed);
    }

    @Test public void frameLimitAdmitsExactBoundaryAndRejectsNextByteBeforeReadingPayload() throws Exception {
        byte[] payload = new byte[512];
        assertArrayEquals(payload, new WebSocketInputStream(new ByteArrayInputStream(medium(512, payload)), 512, 1024).readFrame().getPayload());
        CountingInput input = new CountingInput(medium(513, new byte[0]));
        WebSocketException error = assertThrows(WebSocketException.class,
            () -> new WebSocketInputStream(input, 512, 1024).readFrame());
        assertEquals(WebSocketError.TOO_LONG_PAYLOAD, error.getError());
        assertEquals(4, input.consumed);
        assertEquals(0, input.skipped);
    }

    @Test public void streamBudgetCoversHandshakeAndFrameTogether() throws Exception {
        byte[] input = new byte[]{79,75,13,10,(byte)0x81,3,65,66,67,68};
        WebSocketInputStream stream = new WebSocketInputStream(new ByteArrayInputStream(input), 3, 9);
        assertEquals("OK", stream.readLine());
        assertArrayEquals(new byte[]{65,66,67}, stream.readFrame().getPayload());
        assertThrows(IOException.class, stream::read);
    }

    @Test public void longHandshakeLineStopsAtBudget() throws Exception {
        CountingInput input = new CountingInput(new byte[100]);
        WebSocketInputStream stream = new WebSocketInputStream(input, 0, 16);
        assertThrows(IOException.class, stream::readLine);
        assertEquals(16, input.consumed);
    }

    @Test public void fragmentedMessagesConsumeOneCumulativeBudget() throws Exception {
        CountingInput input = new CountingInput(new byte[]{1,3,65,66,67,(byte)0x80,3,68,69,70});
        WebSocketInputStream stream = new WebSocketInputStream(input, 3, 9);
        assertFalse(stream.readFrame().getFin());
        WebSocketException error = assertThrows(WebSocketException.class, stream::readFrame);
        assertEquals(WebSocketError.TOO_LONG_PAYLOAD, error.getError());
        assertEquals(7, input.consumed);
    }

    @Test public void emptyFrameFloodStillConsumesHeaders() throws Exception {
        CountingInput input = new CountingInput(new byte[]{1,0,0,0,0,0,(byte)0x80,0});
        WebSocketInputStream stream = new WebSocketInputStream(input, 16, 6);
        for (int i = 0; i < 3; i++) assertEquals(0, stream.readFrame().getPayloadLength());
        assertThrows(IOException.class, stream::readFrame);
        assertEquals(6, input.consumed);
    }

    @Test public void bulkReadsCannotOverdrawBudgetAndZeroLengthReadsRemainValid() throws Exception {
        CountingInput input = new CountingInput(new byte[10]);
        WebSocketInputStream stream = new WebSocketInputStream(input, 0, 3);
        assertEquals(3, stream.read(new byte[10]));
        assertEquals(0, stream.read(new byte[0], 0, 0));
        assertThrows(IOException.class, () -> stream.read(new byte[1]));
        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 2, 0));
        assertEquals(3, input.consumed);
    }

    @Test public void skippingConsumesBudgetWithoutIntegerOverflow() throws Exception {
        WebSocketInputStream stream = new WebSocketInputStream(new ByteArrayInputStream(new byte[20]), 0, 4);
        assertEquals(0, stream.skip(-1));
        assertEquals(3, stream.skip(3));
        assertEquals(0, stream.read());
        assertThrows(IOException.class, () -> stream.skip(Long.MAX_VALUE));
    }

    @Test public void negativeLimitsAreRejectedBeforeAnyInput() {
        CountingInput input = new CountingInput(new byte[10]);
        assertThrows(IllegalArgumentException.class, () -> new WebSocketInputStream(input, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketInputStream(input, 0, -1));
        assertEquals(0, input.consumed);
    }

    @Test public void limitsAreOptionalFluentAndPreservedByRecreation() throws Exception {
        WebSocket socket = new WebSocketFactory().createSocket("ws://127.0.0.1:1");
        assertEquals(0, socket.getMaximumIncomingFramePayloadSize());
        assertEquals(0, socket.getMaximumIncomingStreamBytes());
        assertSame(socket, socket.setMaximumIncomingFramePayloadSize(4096));
        assertSame(socket, socket.setMaximumIncomingStreamBytes(16384));
        WebSocket copy = socket.recreate();
        assertEquals(4096, copy.getMaximumIncomingFramePayloadSize());
        assertEquals(16384, copy.getMaximumIncomingStreamBytes());
        socket.disconnect(); copy.disconnect();
    }

    @Test public void invalidOrLateConfigurationCannotChangeLimits() throws Exception {
        WebSocket socket = new WebSocketFactory().createSocket("ws://127.0.0.1:1");
        CountDownLatch closed = new CountDownLatch(1);
        socket.addListener(new WebSocketAdapter() {
            @Override public void onStateChanged(WebSocket websocket, WebSocketState state) {
                if (state == WebSocketState.CLOSED) closed.countDown();
            }
        });
        assertThrows(IllegalArgumentException.class, () -> socket.setMaximumIncomingFramePayloadSize(-1));
        assertThrows(IllegalArgumentException.class, () -> socket.setMaximumIncomingStreamBytes(-1));
        socket.disconnect();
        assertTrue("Asynchronous disconnect must reach CLOSED", closed.await(5, TimeUnit.SECONDS));
        assertEquals(WebSocketState.CLOSED, socket.getState());
        assertThrows(IllegalStateException.class, () -> socket.setMaximumIncomingFramePayloadSize(1));
        assertThrows(IllegalStateException.class, () -> socket.setMaximumIncomingStreamBytes(1));
        assertEquals(0, socket.getMaximumIncomingFramePayloadSize());
        assertEquals(0, socket.getMaximumIncomingStreamBytes());
    }

    private static byte[] extended(long declared, byte[] payload) {
        return ByteBuffer.allocate(10 + payload.length).put((byte)0x81).put((byte)127).putLong(declared).put(payload).array();
    }

    private static byte[] medium(int declared, byte[] payload) {
        return ByteBuffer.allocate(4 + payload.length).put((byte)0x81).put((byte)126).putShort((short)declared).put(payload).array();
    }

    private static final class CountingInput extends ByteArrayInputStream {
        int consumed;
        long skipped;
        CountingInput(byte[] data) { super(data); }
        @Override public synchronized int read() {
            int value = super.read(); if (value >= 0) consumed++; return value;
        }
        @Override public synchronized int read(byte[] data, int offset, int length) {
            int count = super.read(data, offset, length); if (count > 0) consumed += count; return count;
        }
        @Override public synchronized long skip(long length) {
            long count = super.skip(length); skipped += count; return count;
        }
    }
}
