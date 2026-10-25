/*
 * Copyright (C) 2015 Neo Visionaries Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neovisionaries.ws.client;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** One message, one output attempt. Cancellation after acceptance cannot recall bytes. */
public final class GuardedWriteHandle
{
    private final WebSocketWriteGuard mGuard;
    private final GuardedWriteCallback mCallback;
    // QUEUED = 0, committed to synchronous output handoff = 1, TERMINAL = 2.
    private final AtomicInteger mState = new AtomicInteger(0);

    GuardedWriteHandle(WebSocketWriteGuard guard, GuardedWriteCallback callback)
    {
        if (guard == null || callback == null) throw new IllegalArgumentException("Guard and callback are required");
        mGuard = guard;
        mCallback = callback;
    }

    public boolean cancel()
    {
        if (!mState.compareAndSet(0, 2)) return false;
        notifyFailure(new CancellationException("Guarded write cancelled"), false);
        return true;
    }

    boolean isQueued()
    {
        return mState.get() == 0;
    }

    void writeTo(final OutputStream output, final byte[] bytes) throws Exception
    {
        final Thread writer = Thread.currentThread();
        mGuard.writeIfAuthorized(new WebSocketWriteGuard.WriteAction() {
            @Override public void write() throws IOException {
                if (Thread.currentThread() != writer) throw new IllegalStateException("Write action must be synchronous on the writer thread");
                if (!mState.compareAndSet(0, 1)) throw new CancellationException("Guarded write is no longer queued");
                // Use the underlying output directly: failed bytes must never remain in
                // the WebSocket BufferedOutputStream for a later retry/flush.
                output.write(bytes);
                output.flush();
            }
        });
        if (!mState.compareAndSet(1, 2)) throw new IllegalStateException("Guard did not perform exactly one write");
    }

    void fail(Throwable cause)
    {
        int previous = mState.getAndSet(2);
        if (previous != 2) notifyFailure(cause, previous == 1);
    }

    private void notifyFailure(Throwable cause, boolean accepted)
    {
        try {
            mCallback.onFailure(new GuardedWriteFailure(cause, accepted));
        } catch (RuntimeException ignored) {
            // A caller callback cannot replay this message or kill unrelated delivery.
        }
    }
}
