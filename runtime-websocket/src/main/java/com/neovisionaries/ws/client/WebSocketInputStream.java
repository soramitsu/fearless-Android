/*
 * Copyright (C) 2015-2016 Neo Visionaries Inc.
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


import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;


class WebSocketInputStream extends FilterInputStream
{
    private final int mMaximumFramePayloadSize;
    private final int mMaximumStreamBytes;
    private int mConsumedBytes;

    public WebSocketInputStream(InputStream in)
    {
        this(in, 0, 0);
    }


    WebSocketInputStream(InputStream in, int maximumFramePayloadSize, int maximumStreamBytes)
    {
        super(in);
        if (maximumFramePayloadSize < 0 || maximumStreamBytes < 0)
        {
            throw new IllegalArgumentException("Incoming limits must not be negative.");
        }
        mMaximumFramePayloadSize = maximumFramePayloadSize;
        mMaximumStreamBytes = maximumStreamBytes;
    }


    @Override
    public int read() throws IOException
    {
        requireRemainingBytes();
        int value = in.read();
        if (value >= 0 && mMaximumStreamBytes > 0)
        {
            ++mConsumedBytes;
        }
        return value;
    }


    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        if (offset < 0 || length < 0 || length > buffer.length - offset)
        {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0)
        {
            return 0;
        }
        requireRemainingBytes();
        int allowed = mMaximumStreamBytes == 0 ? length :
            Math.min(length, mMaximumStreamBytes - mConsumedBytes);
        int count = in.read(buffer, offset, allowed);
        if (count > 0 && mMaximumStreamBytes > 0)
        {
            mConsumedBytes += count;
        }
        return count;
    }


    @Override
    public long skip(long length) throws IOException
    {
        if (length <= 0)
        {
            return 0;
        }
        requireRemainingBytes();
        long allowed = mMaximumStreamBytes == 0 ? length :
            Math.min(length, mMaximumStreamBytes - mConsumedBytes);
        long count = in.skip(allowed);
        if (count > 0 && mMaximumStreamBytes > 0)
        {
            mConsumedBytes += (int)count;
        }
        return count;
    }


    /**
     * Checks a declared allocation against the remaining parser budget without
     * consuming input. The handshake reader calls this before allocating an
     * error body. This stream has a single reader; this is not a reservation.
     */
    boolean canReadWithoutExceedingLimit(int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("Declared byte count must not be negative.");
        }
        return mMaximumStreamBytes == 0 || length <= mMaximumStreamBytes - mConsumedBytes;
    }


    private void requireRemainingBytes() throws IOException
    {
        if (mMaximumStreamBytes > 0 && mConsumedBytes >= mMaximumStreamBytes)
        {
            throw new IOException("Incoming stream byte limit exceeded.");
        }
    }


    public String readLine() throws IOException
    {
        return Misc.readLine(this, "UTF-8");
    }


    public WebSocketFrame readFrame() throws IOException, WebSocketException
    {
        // Buffer.
        byte[] buffer = new byte[8];

        try
        {
            // Read the first two bytes.
            readBytes(buffer, 2);
        }
        catch (InsufficientDataException e)
        {
            if (e.getReadByteCount() == 0)
            {
                // The connection has been closed without receiving a close frame.
                // Strictly speaking, this is a violation against RFC 6455.
                throw new NoMoreFrameException();
            }
            else
            {
                // Re-throw the exception.
                throw e;
            }
        }

        // FIN
        boolean fin = ((buffer[0] & 0x80) != 0);

        // RSV1, RSV2, RSV3
        boolean rsv1 = ((buffer[0] & 0x40) != 0);
        boolean rsv2 = ((buffer[0] & 0x20) != 0);
        boolean rsv3 = ((buffer[0] & 0x10) != 0);

        // Opcode
        int opcode = (buffer[0] & 0x0F);

        // Mask flag. This should never be true because the specification
        // (RFC 6455, 5. Data Framing, 5.1. Overview) says as follows:
        //
        //     A server MUST NOT mask any frames that it sends to the client.
        //
        boolean mask = ((buffer[1] & 0x80) != 0);

        // The payload length. It is expressed in 7 bits.
        long payloadLength = buffer[1] & 0x7F;

        if (payloadLength == 126)
        {
            // Read the extended payload length.
            // It is expressed in 2 bytes in network byte order.
            readBytes(buffer, 2);

            // Interpret the bytes as a number.
            payloadLength = (((buffer[0] & 0xFF) << 8) |
                             ((buffer[1] & 0xFF)     ));
        }
        else if (payloadLength == 127)
        {
            // Read the extended payload length.
            // It is expressed in 8 bytes in network byte order.
            readBytes(buffer, 8);

            // From RFC 6455, p29.
            //
            //   the most significant bit MUST be 0
            //
            if ((buffer[0] & 0x80) != 0)
            {
                // The payload length in a frame is invalid.
                throw new WebSocketException(
                    WebSocketError.INVALID_PAYLOAD_LENGTH,
                    "The payload length of a frame is invalid.");
            }

            // Interpret the bytes as a number.
            payloadLength = (((buffer[0] & 0xFFL) << 56) |
                             ((buffer[1] & 0xFFL) << 48) |
                             ((buffer[2] & 0xFFL) << 40) |
                             ((buffer[3] & 0xFFL) << 32) |
                             ((buffer[4] & 0xFFL) << 24) |
                             ((buffer[5] & 0xFFL) << 16) |
                             ((buffer[6] & 0xFFL) <<  8) |
                             ((buffer[7] & 0xFFL)      ));
        }

        // Reject declared sizes before allocating or attempting to drain any payload.
        if ((mMaximumFramePayloadSize > 0 && payloadLength > mMaximumFramePayloadSize) ||
            (mMaximumStreamBytes > 0 && payloadLength > mMaximumStreamBytes - mConsumedBytes))
        {
            throw new WebSocketException(WebSocketError.TOO_LONG_PAYLOAD,
                "Incoming frame exceeds the configured response limit.");
        }

        // Masking key
        byte[] maskingKey = null;

        if (mask)
        {
            // Read the masking key. (This should never happen.)
            maskingKey = new byte[4];
            readBytes(maskingKey, 4);
        }

        if (Integer.MAX_VALUE < payloadLength)
        {
            // In Java, the maximum array size is Integer.MAX_VALUE.
            // Reject without draining an untrusted oversized payload.
            throw new WebSocketException(
                WebSocketError.TOO_LONG_PAYLOAD,
                "The payload length of a frame exceeds the maximum array size in Java.");
        }

        // Read the payload if the payload length is not 0.
        byte[] payload = readPayload(payloadLength, mask, maskingKey);

        // Create a WebSocketFrame instance that represents a frame.
        return new WebSocketFrame()
            .setFin(fin)
            .setRsv1(rsv1)
            .setRsv2(rsv2)
            .setRsv3(rsv3)
            .setOpcode(opcode)
            .setMask(mask)
            .setPayload(payload);
    }


    void readBytes(byte[] buffer, int length) throws IOException, WebSocketException
    {
        // Read
        int total = 0;

        while (total < length)
        {
            int count = read(buffer, total, length - total);

            if (count <= 0)
            {
                // The end of the stream has been reached unexpectedly.
                throw new InsufficientDataException(length, total);
            }

            total += count;
        }
    }


    private void skipQuietly(long length)
    {
        try
        {
            skip(length);
        }
        catch (IOException e)
        {
        }
    }


    private byte[] readPayload(long payloadLength, boolean mask, byte[] maskingKey) throws IOException, WebSocketException
    {
        if (payloadLength == 0)
        {
            return null;
        }

        byte[] payload;

        try
        {
            // Allocate a memory area to hold the content of the payload.
            payload = new byte[(int)payloadLength];
        }
        catch (OutOfMemoryError e)
        {
            // OutOfMemoryError occurred during a trial to allocate a memory area
            // for a frame's payload. Skip the payload and raise an exception.
            skipQuietly(payloadLength);
            throw new WebSocketException(
                WebSocketError.INSUFFICIENT_MEMORY_FOR_PAYLOAD,
                "OutOfMemoryError occurred during a trial to allocate a memory area for a frame's payload: " + e.getMessage(), e);
        }

        // Read the payload.
        readBytes(payload, payload.length);

        // If masked.
        if (mask)
        {
            // Unmasked the payload.
            WebSocketFrame.mask(maskingKey, payload);
        }

        return payload;
    }
}
