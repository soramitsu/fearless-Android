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

/**
 * Synchronous authorization at the final output handoff, after frame preparation.
 * The authorizer must invoke the action synchronously at most once on the calling
 * thread, retain no action, and propagate write failures. It must not reenter the
 * writer or wait on work that requires writer progress. Acquire the authority lock
 * and sample fresh policy/clock state immediately before invoking the action.
 * No application callback is made by the library while this method is active.
 */
public interface WebSocketWriteGuard
{
    void writeIfAuthorized(WriteAction write) throws Exception;

    interface WriteAction
    {
        void write() throws IOException;
    }
}
