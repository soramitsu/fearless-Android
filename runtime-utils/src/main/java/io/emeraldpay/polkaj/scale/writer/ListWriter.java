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
import java.util.List;

public class ListWriter<T> implements ScaleWriter<List<T>> {

    private ScaleWriter<T> scaleWriter;

    public ListWriter(ScaleWriter<T> scaleWriter) {
        this.scaleWriter = scaleWriter;
    }

    @Override
    public void write(ScaleCodecWriter wrt, List<T> value) throws IOException {
        wrt.writeCompact(value.size());
        for (T item: value) {
            scaleWriter.write(wrt, item);
        }
    }
}
