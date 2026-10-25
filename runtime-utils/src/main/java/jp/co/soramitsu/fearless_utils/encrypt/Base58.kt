/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.exceptions.AddressFormatException
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays

class Base58 {

    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val INDEXES = IntArray(128)
    private var digest: MessageDigest? = null

    init {
        Arrays.fill(INDEXES, -1)
        for (i in ALPHABET.indices) {
            INDEXES[ALPHABET[i].toInt()] = i
        }

        digest = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) {
            return ""
        }

        var input = copyOfRange(input, 0, input.size)

        var zeroCount = 0
        while (zeroCount < input.size && input[zeroCount].toInt() == 0) {
            ++zeroCount
        }

        val temp = ByteArray(input.size * 2)
        var j = temp.size
        var startAt = zeroCount
        while (startAt < input.size) {
            val mod =
                divmod58(input, startAt)
            if (input[startAt].toInt() == 0) {
                ++startAt
            }
            temp[--j] =
                ALPHABET[mod.toInt()].toByte()
        }

        while (j < temp.size && temp[j].toInt() == ALPHABET[0].toInt()) {
            ++j
        }

        while (--zeroCount >= 0) {
            temp[--j] =
                ALPHABET[0].toByte()
        }
        val output =
            copyOfRange(temp, j, temp.size)
        return try {
            String(output, Charsets.US_ASCII)
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        }
    }

    @Throws(AddressFormatException::class)
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }
        val input58 = ByteArray(input.length)

        for (i in input.indices) {
            val c = input[i]
            var digit58 = -1
            if (c.toInt() in 0..127) {
                digit58 = INDEXES[c.toInt()]
            }
            if (digit58 < 0) {
                throw AddressFormatException("Illegal character $c at $i")
            }
            input58[i] = digit58.toByte()
        }

        var zeroCount = 0
        while (zeroCount < input58.size && input58[zeroCount].toInt() == 0) {
            ++zeroCount
        }

        val temp = ByteArray(input.length)
        var j = temp.size
        var startAt = zeroCount
        while (startAt < input58.size) {
            val mod = divmod256(input58, startAt)
            if (input58[startAt].toInt() == 0) {
                ++startAt
            }
            temp[--j] = mod
        }

        while (j < temp.size && temp[j].toInt() == 0) {
            ++j
        }
        return copyOfRange(
            temp,
            j - zeroCount,
            temp.size
        )
    }

    @Throws(AddressFormatException::class)
    fun decodeToBigInteger(input: String): BigInteger {
        return BigInteger(1, decode(input))
    }

    /**
     * Uses the checksum in the last 4 bytes of the decoded data to verify the rest are correct. The checksum is
     * removed from the returned data.
     *
     * @throws AddressFormatException if the input is not base 58 or the checksum does not validate.
     */
    @Throws(AddressFormatException::class)
    fun decodeChecked(input: String): ByteArray {
        var tmp: ByteArray? = decode(input)

        if (tmp!!.size < 4) {
            throw AddressFormatException("Input to short")
        }

        val bytes = copyOfRange(tmp, 0, tmp.size - 4)
        val checksum = copyOfRange(tmp, tmp.size - 4, tmp.size)
        tmp = doubleDigest(bytes)
        val hash = copyOfRange(tmp, 0, 4)

        if (!checksum.contentEquals(hash)) {
            throw AddressFormatException("Checksum does not validate")
        }

        return bytes
    }

    private fun divmod58(number: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt until number.size) {
            val digit256 = number[i].toInt() and 0xFF
            val temp = remainder * 256 + digit256
            number[i] = (temp / 58).toByte()
            remainder = temp % 58
        }
        return remainder.toByte()
    }

    private fun divmod256(number58: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt until number58.size) {
            val digit58 = number58[i].toInt() and 0xFF
            val temp = remainder * 58 + digit58
            number58[i] = (temp / 256).toByte()
            remainder = temp % 256
        }
        return remainder.toByte()
    }

    private fun copyOfRange(source: ByteArray?, from: Int, to: Int): ByteArray {
        val range = ByteArray(to - from)
        System.arraycopy(source, from, range, 0, range.size)
        return range
    }

    private fun doubleDigest(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size
    ): ByteArray {
        synchronized(digest!!) {
            digest!!.reset()
            digest!!.update(input, offset, length)
            val first = digest!!.digest()
            return digest!!.digest(first)
        }
    }
}
