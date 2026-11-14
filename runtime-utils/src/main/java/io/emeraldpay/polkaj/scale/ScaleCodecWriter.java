/*
 * From polkaj-scale 0.2.3 by EmeraldPay: https://github.com/emeraldpay/polkaj,
 * tag v0.2.3, commit 3227a8ef605cd65b29b1cb2648a02e3a2a9c2419.
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
 *
 * Modified for Fearless Wallet to match the polkaj-scale-0.2.3.jar the app shipped from 2020 to 2026:
 * Optional.isPresent() instead of Optional.isEmpty(); writeString, writeShort and writeLong added.
 */
package io.emeraldpay.polkaj.scale;

import io.emeraldpay.polkaj.scale.writer.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class ScaleCodecWriter implements Closeable {

    public static final CompactUIntWriter COMPACT_UINT = new CompactUIntWriter();
    public static final ShortWriter SHORT_WRITER = new ShortWriter();
    public static final LongWriter LONG_WRITER = new LongWriter();
    public static final UInt16Writer UINT16 = new UInt16Writer();
    public static final UInt32Writer UINT32 = new UInt32Writer();
    public static final ULong32Writer ULONG32 = new ULong32Writer();
    public static final BoolWriter BOOL = new BoolWriter();
    public static final BoolOptionalWriter BOOL_OPT = new BoolOptionalWriter();

    private final OutputStream out;

    public ScaleCodecWriter(OutputStream out) {
        this.out = out;
    }

    public void writeUint256(byte[] value) throws IOException {
        if (value.length != 32) {
            throw new IllegalArgumentException("Value must be 32 byte array");
        }
        out.write(value, 0, value.length);
    }

    public void writeByteArray(byte[] value) throws IOException {
        writeCompact(value.length);
        out.write(value, 0, value.length);
    }

    /**
     * Write the byte into output stream as-is directly, the input is supposed to be already encoded
     *
     * @param b byte to write
     * @throws IOException if failed to write
     */
    public void directWrite(int b) throws IOException {
        out.write(b);
    }

    /**
     * Write the bytes into output stream as-is directly, the input is supposed to be already encoded
     *
     * @param b bytes to write
     * @param off offset
     * @param len length
     * @throws IOException if failed to write
     */
    public void directWrite(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public <T> void write(ScaleWriter<T> writer, T value) throws IOException {
        writer.write(this, value);
    }

    public void writeByte(int value) throws IOException {
        directWrite(value);
    }

    public void writeByte(byte value) throws IOException {
        directWrite(value);
    }

    public void writeUint16(int value) throws IOException {
        UINT16.write(this, value);
    }

    public void writeUint32(int value) throws IOException {
        UINT32.write(this, value);
    }

    public void writeUint32(long value) throws IOException {
        ULONG32.write(this, value);
    }

    public void writeCompact(int value) throws IOException {
        COMPACT_UINT.write(this, value);
    }

    @SuppressWarnings("unchecked")
    public <T> void writeOptional(ScaleWriter<T> writer, T value) throws IOException {
        if (writer instanceof BoolOptionalWriter || writer instanceof BoolWriter) {
            BOOL_OPT.write(this, (Optional<Boolean>) Optional.ofNullable(value));
        } else {
            if (value == null) {
                BOOL.write(this, false);
            } else {
                BOOL.write(this, true);
                writer.write(this, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void writeOptional(ScaleWriter<T> writer, Optional<T> value) throws IOException {
        if (writer instanceof BoolOptionalWriter || writer instanceof BoolWriter) {
            BOOL_OPT.write(this, (Optional<Boolean>) value);
        } else {
            if (value.isPresent()) {
                BOOL.write(this, true);
                writer.write(this, value.get());
            } else {
                BOOL.write(this, false);
            }
        }
    }

    /**
     * Write string, encoded as UTF-8 bytes with a compact length prefix
     * @param value string value
     * @throws IOException if failed to write
     */
    public void writeString(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeByteArray(bytes);
    }

    public void writeShort(Short value) throws IOException {
        SHORT_WRITER.write(this, value);
    }

    public void writeLong(Long value) throws IOException {
        LONG_WRITER.write(this, value);
    }
}
