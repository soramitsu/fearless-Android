package jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.checksumCoder

import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonChecksumCoder
import jp.co.soramitsu.fearless_utils.extensions.split
import java.lang.Exception

private val PKCS8_HEADER = intArrayOf(48, 83, 2, 1, 1, 48, 5, 6, 3, 43, 101, 112, 4, 34, 4, 32)
    .map(Int::toByte)
    .toByteArray()

private val PKCS8_DIVIDER = intArrayOf(161, 35, 3, 33, 0)
    .map(Int::toByte)
    .toByteArray()

object IncorrectPkcs8Checksum : Exception("Incorrect Pkcs8 checksum")

object Pkcs8ChecksumCoder : JsonChecksumCoder {

    override fun encode(values: List<ByteArray>): ByteArray {
        return values.foldIndexed(PKCS8_HEADER) { index, acc, element ->
            if (index > 0) {
                acc + PKCS8_DIVIDER + element
            } else {
                acc + element
            }
        }
    }

    override fun decode(data: ByteArray): List<ByteArray> {
        val headerAndRest = data.split(PKCS8_HEADER)

        if (headerAndRest.size != 2) throw IncorrectPkcs8Checksum

        val rest = headerAndRest[1]

        val encodedSecrets = rest.split(PKCS8_DIVIDER)

        if (encodedSecrets.size != 2) throw IncorrectPkcs8Checksum

        return encodedSecrets
    }
}
