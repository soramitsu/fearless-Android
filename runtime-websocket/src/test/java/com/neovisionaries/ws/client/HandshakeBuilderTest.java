package com.neovisionaries.ws.client;

import java.net.URI;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HandshakeBuilderTest {
    @Test
    public void requestPreservesPathQueryAndProtocolPreferenceWithoutDuplicates() {
        HandshakeBuilder builder = new HandshakeBuilder(true, null, "node.invalid:9944", "/rpc?chain=dot");
        builder.setKey("public-test-key");
        builder.addProtocol("json-rpc");
        builder.addProtocol("binary");
        builder.addProtocol("json-rpc");
        assertEquals(URI.create("wss://node.invalid:9944/rpc?chain=dot"), builder.getURI());
        assertEquals("json-rpc, binary", header(builder, "Sec-WebSocket-Protocol"));
        assertEquals("node.invalid:9944", header(builder, "Host"));
        assertEquals("Upgrade", header(builder, "Connection"));
        assertEquals("websocket", header(builder, "Upgrade"));
        assertEquals("13", header(builder, "Sec-WebSocket-Version"));
        String request = HandshakeBuilder.build(builder.buildRequestLine(), builder.buildHeaders());
        assertTrue(request.startsWith("GET /rpc?chain=dot HTTP/1.1\r\n"));
        assertTrue(request.contains("Sec-WebSocket-Key: public-test-key\r\n"));
        assertTrue(request.endsWith("\r\n\r\n"));
    }

    @Test
    public void invalidProtocolsRejectAndRemovalLeavesNoEmptyHeader() {
        HandshakeBuilder builder = empty();
        for (String invalid : new String[] {null, "", "has space", "comma,", "\u007f", "snow\u96ea"}) {
            assertThrows(IllegalArgumentException.class, () -> builder.addProtocol(invalid));
        }
        builder.removeProtocol(null);
        builder.removeProtocol("absent");
        assertFalse(builder.containsProtocol("absent"));
        builder.addProtocol("rpc");
        assertTrue(builder.containsProtocol("rpc"));
        builder.removeProtocol("rpc");
        assertNull(header(builder, "Sec-WebSocket-Protocol"));
        builder.addProtocol("rpc");
        builder.clearProtocols();
        assertFalse(builder.containsProtocol("rpc"));
    }

    @Test
    public void copyOwnsIndependentHeadersProtocolsAndExtensionParameters() {
        HandshakeBuilder original = empty();
        original.addProtocol("rpc");
        WebSocketExtension extension = new WebSocketExtension("custom").setParameter("mode", "one");
        original.addExtension(extension);
        original.addHeader("X-Trace", "original");
        HandshakeBuilder copy = new HandshakeBuilder(original);
        extension.setParameter("mode", "two");
        for (String[] value : original.buildHeaders()) {
            if (value[0].equals("X-Trace")) value[1] = "mutated";
        }
        original.clearProtocols();
        original.clearExtensions();
        original.clearHeaders();
        assertTrue(copy.containsProtocol("rpc"));
        assertEquals("custom; mode=one", header(copy, "Sec-WebSocket-Extensions"));
        assertEquals("original", header(copy, "X-Trace"));
        assertNull(header(original, "X-Trace"));
        assertEquals(5, new HandshakeBuilder(empty()).buildHeaders().size());
    }

    @Test
    public void extensionRemovalSupportsIdentityAndAllExtensionsWithOneName() {
        HandshakeBuilder builder = empty();
        builder.addExtension((WebSocketExtension) null);
        builder.removeExtension(null);
        builder.removeExtensions(null);
        builder.removeExtensions("absent");
        assertFalse(builder.containsExtension((WebSocketExtension) null));
        assertFalse(builder.containsExtension((String) null));
        assertFalse(builder.containsExtension("absent"));
        WebSocketExtension extension = new WebSocketExtension("custom");
        builder.addExtension(extension);
        builder.addExtension("another; flag");
        builder.addExtension("custom; flag");
        assertTrue(builder.containsExtension(extension));
        assertFalse(builder.containsExtension("absent"));
        builder.removeExtensions("custom");
        assertFalse(builder.containsExtension(extension));
        assertTrue(builder.containsExtension("another"));
        builder.removeExtensions("another");
        assertNull(header(builder, "Sec-WebSocket-Extensions"));
        builder.addExtension(extension);
        builder.removeExtension(extension);
        assertNull(header(builder, "Sec-WebSocket-Extensions"));
    }

    @Test
    public void basicAuthenticationAndCustomHeadersCanBeClearedIndependently() {
        HandshakeBuilder builder = empty();
        builder.addHeader(null, "ignored");
        builder.addHeader("", "ignored");
        builder.removeHeaders(null);
        builder.removeHeaders("");
        builder.removeHeaders("absent");
        builder.setUserInfo("user", "pass");
        assertEquals("Basic dXNlcjpwYXNz", header(builder, "Authorization"));
        builder.setUserInfo(null, null);
        assertEquals("Basic Og==", header(builder, "Authorization"));
        builder.setUserInfo("");
        assertNull(header(builder, "Authorization"));
        builder.setUserInfo("public:fixture");
        builder.addHeader("X-Empty", null);
        builder.addHeader("X-Remove", "one");
        builder.addHeader("X-Remove", "two");
        assertEquals("", header(builder, "X-Empty"));
        builder.removeHeaders("X-Remove");
        assertNull(header(builder, "X-Remove"));
        builder.removeHeaders("X-Empty");
        builder.clearUserInfo();
        assertEquals(5, builder.buildHeaders().size());
    }

    private static HandshakeBuilder empty() {
        return new HandshakeBuilder(false, null, "node.invalid", "/");
    }

    private static String header(HandshakeBuilder builder, String name) {
        List<String[]> headers = builder.buildHeaders();
        for (String[] header : headers) {
            if (header[0].equals(name)) return header[1];
        }
        return null;
    }
}
