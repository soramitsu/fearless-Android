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

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

/**
 * Read a Java enum value. The reader reads a one byte and returns a Enum value which Ordinal value is equal to it.
 * <br>
 * If you need to read an enumeration with assigned value, i.e. Rust style enum, you should use {@link UnionReader} instead.
 *
 * @param <T> type of Enum
 * @see UnionReader
 */
public class EnumReader<T extends Enum<?>> implements ScaleReader<T> {

    private final T[] values;

    /**
     * Define reader by specifying list of possible values. In most of the cases it would be:
     * <code>new EnumReader(MyEnum.values()</code>
     *
     * @param values list of enum values
     */
    public EnumReader(T[] values) {
        if (values == null) {
            throw new NullPointerException("List of enums is null");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("List of enums is empty");
        }
        this.values = values;
    }

    @Override
    public T read(ScaleCodecReader rdr) {
        int id = rdr.readUByte();
        for (T t: values) {
            if (t.ordinal() == id) {
                return t;
            }
        }
        throw new IllegalStateException("Unknown enum value: " + id);
    }
}
