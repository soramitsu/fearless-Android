package com.neovisionaries.ws.client;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Exercise the actual handshake reader before any error-body allocation. */
public class HandshakeResponseBudgetTest {
    @Test public void rejectedResponseOverRemainingBudgetDoesNotReadOrDrainBody() throws Exception {
        byte[] header = rejectedHeader(33);
        CountingInput input = new CountingInput(join(header, new byte[33]));
        AdmissionInput stream = new AdmissionInput(input, header.length + 32);
        OpeningHandshakeException failure = rejected(stream);
        assertNull(failure.getBody());
        assertEquals(1, stream.admissionChecks);
        assertEquals(33, stream.declaredLength);
        assertEquals(header.length, input.consumed);
        assertEquals(0, input.skipped);
    }

    @Test public void maximumDeclaredErrorBodyRequiresAdmissionBeforeAllocation() throws Exception {
        byte[] header = rejectedHeader(Integer.MAX_VALUE);
        CountingInput input = new CountingInput(join(header, new byte[]{73}));
        AdmissionInput stream = new AdmissionInput(input, header.length + 8);
        assertNull(rejected(stream).getBody());
        assertEquals(1, stream.admissionChecks);
        assertEquals(Integer.MAX_VALUE, stream.declaredLength);
        assertEquals(header.length, input.consumed);
        assertEquals(0, input.skipped);
    }

    @Test public void exactRemainingErrorBodyIsRetained() throws Exception {
        byte[] body = "PUBLIC_FIXTURE_BODY".getBytes(StandardCharsets.US_ASCII);
        byte[] header = rejectedHeader(body.length);
        CountingInput input = new CountingInput(join(header, body));
        AdmissionInput stream = new AdmissionInput(input, header.length + body.length);
        assertArrayEquals(body, rejected(stream).getBody());
        assertEquals(1, stream.admissionChecks);
        assertEquals(body.length, stream.declaredLength);
        assertEquals(header.length + body.length, input.consumed);
        assertThrows(IOException.class, stream::read);
    }

    @Test public void headersExhaustingBudgetLeaveErrorBodyUnread() throws Exception {
        byte[] header = rejectedHeader(1);
        CountingInput input = new CountingInput(join(header, new byte[]{73}));
        AdmissionInput stream = new AdmissionInput(input, header.length);
        assertNull(rejected(stream).getBody());
        assertEquals(1, stream.admissionChecks);
        assertEquals(header.length, input.consumed);
        assertEquals(0, input.skipped);
    }

    @Test public void unlimitedStreamRetainsPriorErrorBodyBehavior() throws Exception {
        byte[] body = new byte[]{65, 0, 66};
        byte[] header = rejectedHeader(body.length);
        CountingInput input = new CountingInput(join(header, body));
        AdmissionInput stream = new AdmissionInput(input, 0);
        assertArrayEquals(body, rejected(stream).getBody());
        assertEquals(1, stream.admissionChecks);
        assertEquals(header.length + body.length, input.consumed);
    }

    @Test public void successfulActualHandshakeAndFrameShareOneBudget() throws Exception {
        byte[] header = ("HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[]{(byte)0x81, 3, 65, 66, 67};
        CountingInput input = new CountingInput(join(header, frame));
        WebSocketInputStream stream = new WebSocketInputStream(input, 3, header.length + frame.length);
        WebSocket socket = new WebSocketFactory().createSocket("ws://127.0.0.1:1");
        try {
            new HandshakeReader(socket).readHandshake(stream, "dGhlIHNhbXBsZSBub25jZQ==");
            assertArrayEquals(new byte[]{65, 66, 67}, stream.readFrame().getPayload());
            assertEquals(header.length + frame.length, input.consumed);
            assertThrows(IOException.class, stream::read);
        } finally {
            socket.disconnect();
        }
    }

    private static OpeningHandshakeException rejected(WebSocketInputStream stream) throws Exception {
        WebSocket socket = new WebSocketFactory().createSocket("ws://127.0.0.1:1");
        try {
            OpeningHandshakeException failure = assertThrows(OpeningHandshakeException.class,
                () -> new HandshakeReader(socket).readHandshake(stream, "unused-public-key"));
            assertEquals(WebSocketError.NOT_SWITCHING_PROTOCOLS, failure.getError());
            assertEquals(400, failure.getStatusLine().getStatusCode());
            return failure;
        } finally {
            socket.disconnect();
        }
    }

    private static byte[] rejectedHeader(int length) {
        return ("HTTP/1.1 400 Bad Request\r\nContent-Length: " + length + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] join(byte[] first, byte[] second) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(first); result.write(second);
        return result.toByteArray();
    }

    private static final class AdmissionInput extends WebSocketInputStream {
        int admissionChecks;
        int declaredLength = -1;
        AdmissionInput(CountingInput input, int streamLimit) { super(input, 1, streamLimit); }
        @Override boolean canReadWithoutExceedingLimit(int length) {
            admissionChecks++;
            declaredLength = length;
            return super.canReadWithoutExceedingLimit(length);
        }
    }

    private static final class CountingInput extends ByteArrayInputStream {
        int consumed;
        long skipped;
        CountingInput(byte[] bytes) { super(bytes); }
        @Override public synchronized int read() {
            int value = super.read(); if (value >= 0) consumed++; return value;
        }
        @Override public synchronized int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, length); if (count > 0) consumed += count; return count;
        }
        @Override public synchronized long skip(long length) {
            long count = super.skip(length); skipped += count; return count;
        }
    }
}
