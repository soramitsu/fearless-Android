package jp.co.soramitsu.common.utils.solana

import jp.co.soramitsu.common.domain.SOLANA_DEFAULT_PATH
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

data class SolanaKeypair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

object SolanaKeyFactory {

    private const val HARDENED_BIT = 0x80000000.toInt()
    private val ED25519_SEED = "ed25519 seed".toByteArray()

    fun deriveKeypair(
        mnemonicWords: List<String>,
        derivationPath: String = SOLANA_DEFAULT_PATH
    ): SolanaKeypair {
        val seed = Mnemonic.toSeed(mnemonicWords)
        val master = slip10Master(seed)
        val indices = parsePath(derivationPath)

        val finalNode = indices.fold(master) { current, index ->
            deriveChild(current, index)
        }

        val privateKey = finalNode.key
        val keypair = PrivateKeyEd25519(privateKey)
        val publicKey = keypair.publicKey().key.toByteArray()

        return SolanaKeypair(
            privateKey = privateKey,
            publicKey = publicKey
        )
    }

    private fun slip10Master(seed: ByteArray): Slip10Node {
        val digest = hmacSha512(ED25519_SEED, seed)
        val key = digest.copyOfRange(0, 32)
        val chainCode = digest.copyOfRange(32, 64)
        return Slip10Node(key, chainCode)
    }

    private fun deriveChild(parent: Slip10Node, index: Int): Slip10Node {
        val indexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array()
        val data = ByteArray(1 + parent.key.size + 4)
        data[0] = 0
        parent.key.copyInto(data, destinationOffset = 1)
        indexBytes.copyInto(data, destinationOffset = 1 + parent.key.size)

        val digest = hmacSha512(parent.chainCode, data)
        val key = digest.copyOfRange(0, 32)
        val chainCode = digest.copyOfRange(32, 64)

        return Slip10Node(key, chainCode)
    }

    private fun parsePath(path: String): List<Int> {
        val raw = path.removePrefix("m/")
        if (raw.isEmpty()) return emptyList()

        return raw.split("/")
            .filter { it.isNotEmpty() }
            .map { component ->
                val hardened = component.endsWith("'")
                val number = component.removeSuffix("'").toInt()
                if (hardened) {
                    number or HARDENED_BIT
                } else {
                    number
                }
            }
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA512")
        mac.init(secretKeySpec)
        return mac.doFinal(data)
    }

    private data class Slip10Node(
        val key: ByteArray,
        val chainCode: ByteArray
    )
}
