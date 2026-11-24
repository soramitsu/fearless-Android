package com.neovisionaries.ws.client;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(Parameterized.class)
public class ListenerManagerDispatchTest {
    private final String callback;
    private final Consumer<ListenerManager> dispatch;
    private final Object[] arguments;

    public ListenerManagerDispatchTest(String callback, Consumer<ListenerManager> dispatch, Object[] arguments) {
        this.callback = callback;
        this.dispatch = dispatch;
        this.arguments = arguments;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> events() {
        WebSocketFrame frame = WebSocketFrame.createTextFrame("rpc response");
        WebSocketFrame serverClose = WebSocketFrame.createCloseFrame(1000, "server done");
        WebSocketFrame clientClose = WebSocketFrame.createCloseFrame(1000, "client done");
        WebSocketException error = new WebSocketException(WebSocketError.SOCKET_CONNECT_ERROR, "fixture failure");
        byte[] bytes = new byte[] {1, 2, 3};
        List<WebSocketFrame> frames = Collections.singletonList(frame);
        Map<String, List<String>> headers = Collections.singletonMap("Upgrade", Collections.singletonList("websocket"));
        List<String[]> requestHeaders = Collections.singletonList(new String[] {"Host", "node.invalid"});
        Thread thread = new Thread();
        ThreadType type = ThreadType.READING_THREAD;
        return Arrays.asList(
            event("onStateChanged", m -> m.callOnStateChanged(WebSocketState.OPEN), WebSocketState.OPEN),
            event("onConnected", m -> m.callOnConnected(headers), headers),
            event("onConnectError", m -> m.callOnConnectError(error), error),
            event("onDisconnected", m -> m.callOnDisconnected(serverClose, clientClose, true), serverClose, clientClose, true),
            event("onFrame", m -> m.callOnFrame(frame), frame),
            event("onContinuationFrame", m -> m.callOnContinuationFrame(frame), frame),
            event("onTextFrame", m -> m.callOnTextFrame(frame), frame),
            event("onBinaryFrame", m -> m.callOnBinaryFrame(frame), frame),
            event("onCloseFrame", m -> m.callOnCloseFrame(serverClose), serverClose),
            event("onPingFrame", m -> m.callOnPingFrame(frame), frame),
            event("onPongFrame", m -> m.callOnPongFrame(frame), frame),
            event("onTextMessage", m -> m.callOnTextMessage("response"), "response"),
            event("onTextMessage", m -> m.callOnTextMessage(bytes), bytes),
            event("onBinaryMessage", m -> m.callOnBinaryMessage(bytes), bytes),
            event("onSendingFrame", m -> m.callOnSendingFrame(frame), frame),
            event("onFrameSent", m -> m.callOnFrameSent(frame), frame),
            event("onFrameUnsent", m -> m.callOnFrameUnsent(frame), frame),
            event("onThreadCreated", m -> m.callOnThreadCreated(type, thread), type, thread),
            event("onThreadStarted", m -> m.callOnThreadStarted(type, thread), type, thread),
            event("onThreadStopping", m -> m.callOnThreadStopping(type, thread), type, thread),
            event("onError", m -> m.callOnError(error), error),
            event("onFrameError", m -> m.callOnFrameError(error, frame), error, frame),
            event("onMessageError", m -> m.callOnMessageError(error, frames), error, frames),
            event("onMessageDecompressionError", m -> m.callOnMessageDecompressionError(error, bytes), error, bytes),
            event("onTextMessageError", m -> m.callOnTextMessageError(error, bytes), error, bytes),
            event("onSendError", m -> m.callOnSendError(error, frame), error, frame),
            event("onUnexpectedError", m -> m.callOnUnexpectedError(error), error),
            event("onSendingHandshake", m -> m.callOnSendingHandshake("GET / HTTP/1.1", requestHeaders),
                "GET / HTTP/1.1", requestHeaders)
        );
    }

    @Test
    public void everyListenerReceivesOriginalSocketAndArgumentsInRegistrationOrder() throws Exception {
        WebSocket socket = new WebSocketFactory().createSocket("ws://node.invalid");
        ListenerManager manager = new ListenerManager(socket);
        List<Invocation> invocations = new ArrayList<>();
        manager.addListener(recordingListener("first", invocations, null));
        manager.addListener(recordingListener("second", invocations, null));

        dispatch.accept(manager);
        dispatch.accept(manager);

        assertEquals(4, invocations.size());
        for (int index = 0; index < invocations.size(); index++) {
            Invocation invocation = invocations.get(index);
            assertEquals(index % 2 == 0 ? "first" : "second", invocation.listener);
            assertEquals(callback, invocation.method);
            assertSame(socket, invocation.arguments[0]);
            assertArrayEquals(arguments, Arrays.copyOfRange(invocation.arguments, 1, invocation.arguments.length));
            for (int argument = 0; argument < arguments.length; argument++) {
                assertSame(arguments[argument], invocation.arguments[argument + 1]);
            }
        }
    }

    @Test
    public void callbackAndErrorHandlerFailuresDoNotPreventDeliveryToNextListener() throws Exception {
        WebSocket socket = new WebSocketFactory().createSocket("ws://node.invalid");
        ListenerManager manager = new ListenerManager(socket);
        List<Invocation> invocations = new ArrayList<>();
        AssertionError failure = new AssertionError("listener failed");
        manager.addListener(recordingListener("broken", invocations, failure));
        manager.addListener(recordingListener("healthy", invocations, null));

        dispatch.accept(manager);

        assertEquals(3, invocations.size());
        assertEquals(callback, invocations.get(0).method);
        assertEquals("broken", invocations.get(0).listener);
        Invocation reported = invocations.get(1);
        assertEquals("broken", reported.listener);
        assertEquals("handleCallbackError", reported.method);
        assertArrayEquals(new Object[] {socket, failure}, reported.arguments);
        Invocation delivered = invocations.get(2);
        assertEquals("healthy", delivered.listener);
        assertEquals(callback, delivered.method);
        assertSame(socket, delivered.arguments[0]);
        assertArrayEquals(arguments, Arrays.copyOfRange(delivered.arguments, 1, delivered.arguments.length));
    }

    private static Object[] event(String callback, Consumer<ListenerManager> dispatch, Object... arguments) {
        return new Object[] {callback, dispatch, arguments};
    }

    private static WebSocketListener recordingListener(String name, List<Invocation> invocations, Throwable failure) {
        return (WebSocketListener) Proxy.newProxyInstance(
            WebSocketListener.class.getClassLoader(), new Class<?>[] {WebSocketListener.class},
            (proxy, method, arguments) -> {
                invocations.add(new Invocation(name, method.getName(), arguments));
                if (failure != null) throw failure;
                return null;
            });
    }

    private static final class Invocation {
        final String listener;
        final String method;
        final Object[] arguments;

        Invocation(String listener, String method, Object[] arguments) {
            this.listener = listener;
            this.method = method;
            this.arguments = arguments;
        }
    }
}
