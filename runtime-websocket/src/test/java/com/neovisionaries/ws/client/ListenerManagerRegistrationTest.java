package com.neovisionaries.ws.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ListenerManagerRegistrationTest {
    @Test
    public void nullAndAbsentListenersDoNotChangeRegistration() throws Exception {
        ListenerManager manager = new ListenerManager(new WebSocketFactory().createSocket("ws://node.invalid"));
        WebSocketListener first = new WebSocketAdapter();
        WebSocketListener second = new WebSocketAdapter();
        manager.addListener(null);
        manager.addListeners(null);
        manager.addListeners(Arrays.asList(null, first, second));
        manager.removeListener(null);
        manager.removeListener(new WebSocketAdapter());
        manager.removeListeners(null);
        assertEquals(Arrays.asList(first, second), manager.getListeners());

        manager.removeListeners(Arrays.asList(null, first, new WebSocketAdapter()));
        assertEquals(Collections.singletonList(second), manager.getListeners());
        manager.removeListener(second);
        assertTrue(manager.getListeners().isEmpty());
        manager.clearListeners();
        assertTrue(manager.getListeners().isEmpty());
    }

    @Test
    public void changesDuringDeliveryAffectNextEventWithoutInterruptingCurrentSnapshot() throws Exception {
        ListenerManager manager = new ListenerManager(new WebSocketFactory().createSocket("ws://node.invalid"));
        List<String> messages = new ArrayList<>();
        WebSocketListener removed = recordingListener("removed", messages);
        WebSocketListener added = recordingListener("added", messages);
        WebSocketListener mutating = new WebSocketAdapter() {
            @Override
            public void onTextMessage(WebSocket socket, String text) {
                messages.add("mutating:" + text);
                manager.removeListener(this);
                manager.removeListener(removed);
                manager.addListener(added);
            }
        };
        manager.addListeners(Arrays.asList(mutating, removed));
        manager.callOnTextMessage("first");
        manager.callOnTextMessage("next");
        assertEquals(Arrays.asList("mutating:first", "removed:first", "added:next"), messages);
        assertEquals(Collections.singletonList(added), manager.getListeners());
    }

    @Test
    public void clearingListenersInvalidatesCachedSnapshotAndAllowsFreshRegistration() throws Exception {
        ListenerManager manager = new ListenerManager(new WebSocketFactory().createSocket("ws://node.invalid"));
        List<String> messages = new ArrayList<>();
        manager.addListener(recordingListener("old", messages));
        manager.callOnTextMessage("before");
        manager.clearListeners();
        manager.callOnTextMessage("ignored");
        manager.clearListeners();
        manager.addListener(recordingListener("new", messages));
        manager.callOnTextMessage("after");
        assertEquals(Arrays.asList("old:before", "new:after"), messages);
        assertEquals(1, manager.getListeners().size());
    }

    private static WebSocketListener recordingListener(String name, List<String> messages) {
        return new WebSocketAdapter() {
            @Override
            public void onTextMessage(WebSocket socket, String text) {
                messages.add(name + ":" + text);
            }
        };
    }
}
