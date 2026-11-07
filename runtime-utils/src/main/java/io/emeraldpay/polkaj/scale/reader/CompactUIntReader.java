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
 */
package io.emeraldpay.polkaj.scale.reader;

import io.emeraldpay.polkaj.scale.CompactMode;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;

public class CompactUIntReader implements ScaleReader<Integer> {

    /**
     *
     * @param rdr reader with the encoded data
     * @return integer value
     * @throws UnsupportedOperationException if the value is encoded with more than four bytes (use {@link CompactBigIntReader})
     */
    @Override
    public Integer read(ScaleCodecReader rdr) {
        int i = rdr.readUByte();
        CompactMode mode = CompactMode.byValue((byte)(i & 0b11));
        if (mode == CompactMode.SINGLE) {
            return i >> 2;
        }
        if (mode == CompactMode.TWO) {
            return (i >> 2)
                    + (rdr.readUByte() << 6);
        }
        if (mode == CompactMode.FOUR) {
            return (i >> 2) +
                    (rdr.readUByte() << 6) +
                    (rdr.readUByte() << (6 + 8)) +
                    (rdr.readUByte() << (6 + 2 * 8));
        }
        throw new UnsupportedOperationException("Mode " + mode  + " is not implemented");
    }
}
