package jp.co.soramitsu.fearless_utils.encrypt.qr

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString

private const val PREFIX = "substrate"

const val DELIMITER = ":"

private const val PARTS_WITH_NAME = 4
private const val PARTS_WITHOUT_NAME = 3

object QrSharing {
    class InvalidFormatException : Exception()

    class Payload(
        val address: String,
        val publicKey: ByteArray,
        val name: String?
    )

    fun encode(payload: Payload): String {
        return with(payload) {
            val publicKeyEncoded = publicKey.toHexString(withPrefix = true)

            val withoutName = "$PREFIX$DELIMITER$address$DELIMITER$publicKeyEncoded"

            if (name != null) "$withoutName$DELIMITER$name" else withoutName
        }
    }

    fun decode(qrContent: String): Payload {
        val parts = qrContent.split(DELIMITER)

        if (parts.size !in PARTS_WITHOUT_NAME..PARTS_WITH_NAME) throw InvalidFormatException()

        val (prefix, address, publicKeyEncoded) = parts

        if (prefix != PREFIX) throw InvalidFormatException()

        val name = if (parts.size == PARTS_WITH_NAME) {
            parts.last()
        } else {
            null
        }

        return Payload(address, publicKeyEncoded.fromHex(), name)
    }
}
