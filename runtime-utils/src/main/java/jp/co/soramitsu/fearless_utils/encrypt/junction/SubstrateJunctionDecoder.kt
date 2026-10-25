package jp.co.soramitsu.fearless_utils.encrypt.junction

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b128
import jp.co.soramitsu.fearless_utils.scale.dataType.string
import jp.co.soramitsu.fearless_utils.scale.dataType.toByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val CHAINCODE_LENGTH = 32

object SubstrateJunctionDecoder : JunctionDecoder() {

    override fun decodeJunction(rawJunction: String, type: JunctionType): Junction {
        val chainCode = normalize(serialize(rawJunction))

        return Junction(type, chainCode)
    }

    private fun serialize(rawJunction: String): ByteArray {
        rawJunction.toLongOrNull()?.let {
            val bytes = ByteArray(8)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(it)

            return bytes
        }

        return runCatching {
            rawJunction.fromHex()
        }.getOrElse {
            string.toByteArray(rawJunction)
        }
    }

    private fun normalize(bytes: ByteArray): ByteArray = when {
        bytes.size < CHAINCODE_LENGTH -> ByteArray(CHAINCODE_LENGTH).apply {
            bytes.copyInto(this)
        }
        bytes.size > CHAINCODE_LENGTH -> bytes.blake2b128()
        else -> bytes
    }
}
