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
 * BigInteger.valueOf(2) instead of BigInteger.TWO (Android before 13 lacks it).
 */
package io.emeraldpay.polkaj.scale;

import java.math.BigInteger;

public enum CompactMode {

    SINGLE((byte)0b00),
    TWO((byte)0b01),
    FOUR((byte)0b10),
    BIGINT((byte)0b11);

    private static BigInteger MAX = BigInteger.valueOf(2).pow(536).subtract(BigInteger.ONE);

    private byte value;

    private CompactMode(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static CompactMode byValue(byte value) {
        if (value == SINGLE.value) {
            return SINGLE;
        } else if (value == TWO.value) {
            return TWO;
        } else if (value == FOUR.value) {
            return FOUR;
        } else {
            return BIGINT;
        }
    }

    public static CompactMode forNumber(int number) {
        return forNumber((long)number);
    }

    public static CompactMode forNumber(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("Negative numbers are not supported");
        }
        if (number <= 0x3f) {
            return CompactMode.SINGLE;
        } else if (number <= 0x3fff) {
            return CompactMode.TWO;
        } else if (number <= 0x3fffffff) {
            return CompactMode.FOUR;
        } else {
            return CompactMode.BIGINT;
        }
    }

    public static CompactMode forNumber(BigInteger number) {
        if (number.signum() < 0) {
            throw new IllegalArgumentException("Negative numbers are not supported");
        }
        if (number.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Numbers larger than 2**536-1 are not supported");
        }
        if (number.equals(BigInteger.ZERO)) {
            return CompactMode.SINGLE;
        } else if (number.compareTo(BigInteger.valueOf(0x3fffffff)) > 0) {
            return CompactMode.BIGINT;
        } else if (number.compareTo(BigInteger.valueOf(0x3fff)) > 0) {
            return CompactMode.FOUR;
        } else if (number.compareTo(BigInteger.valueOf(0x3f)) > 0) {
            return CompactMode.TWO;
        } else {
            return CompactMode.SINGLE;
        }
    }

}
