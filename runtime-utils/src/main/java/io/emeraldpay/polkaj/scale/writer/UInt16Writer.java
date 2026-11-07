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
package io.emeraldpay.polkaj.scale.writer;

import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;

import java.io.IOException;

public class UInt16Writer implements ScaleWriter<Integer> {
    @Override
    public void write(ScaleCodecWriter wrt, Integer value) throws IOException {
        wrt.directWrite(value & 0xff);
        wrt.directWrite((value >> 8) & 0xff);
    }
}
