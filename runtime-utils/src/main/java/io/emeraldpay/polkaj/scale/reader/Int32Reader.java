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
 * Buffer.flip() instead of the Java 9 ByteBuffer.flip() overload.
 */
package io.emeraldpay.polkaj.scale.reader;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Read Java Integer encoded as 4 byte SCALE value. Please note that since Java Integer is signed type, it may
 * read negative values for some of the byte representations (i.e. when highest bit is set to 1). If you expect
 * to read positive numbers for all of the possible range, you should use Uint32Reader, which returns Long values.
 *
 * @see UInt32Reader
 */
public class Int32Reader implements ScaleReader<Integer> {
    @Override
    public Integer read(ScaleCodecReader rdr) {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(rdr.readByte());
        buf.put(rdr.readByte());
        buf.put(rdr.readByte());
        buf.put(rdr.readByte());
        ((Buffer) buf).flip();
        return buf.getInt();
    }
}
