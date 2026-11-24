/*
 * Added to polkaj-scale 0.2.3 (https://github.com/emeraldpay/polkaj, Apache License 2.0) for Fearless Wallet.
 * It reproduces the class of the same name in the polkaj-scale-0.2.3.jar the app shipped from 2020 to 2026.
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

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

/**
 * Write string as UTF-8 bytes with a compact length prefix.
 */
public class StringWriter implements ScaleWriter<String> {
    @Override
    public void write(ScaleCodecWriter wrt, String value) throws IOException {
        wrt.writeString(value);
    }
}
