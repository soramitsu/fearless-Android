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

import java.math.BigInteger;

public class CompactBigIntReader implements ScaleReader<BigInteger> {

    private static final CompactUIntReader intReader = new CompactUIntReader();

    @Override
    public BigInteger read(ScaleCodecReader rdr) {
        int type = rdr.readUByte();
        CompactMode mode = CompactMode.byValue((byte)(type & 0b11));
        if (mode != CompactMode.BIGINT) {
            rdr.skip(-1);
            int value = intReader.read(rdr);
            return BigInteger.valueOf(value);
        }
        int len = (type >> 2) + 4;
        byte[] value = rdr.readByteArray(len);
        //LE encoded, so need to reverse it
        for (int i = 0; i < value.length / 2; i++) {
            byte temp = value[i];
            value[i] = value[value.length -i -1];
            value[value.length - i - 1] = temp;
        }
        //unsigned, i.e. always positive, signum=1
        return new BigInteger(1, value);
    }
}
