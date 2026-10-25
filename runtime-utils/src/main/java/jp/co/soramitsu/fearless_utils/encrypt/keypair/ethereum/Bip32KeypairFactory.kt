package jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum

import jp.co.soramitsu.fearless_utils.encrypt.hmacSHA512
import jp.co.soramitsu.fearless_utils.encrypt.junction.Junction
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.keypair.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.derivePublicKey
import jp.co.soramitsu.fearless_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.fearless_utils.extensions.requireOrException
import jp.co.soramitsu.fearless_utils.scale.utils.toUnsignedBytes
import java.math.BigInteger

object Bip32KeypairFactory : KeypairFactory<Bip32ExtendedKeyPair> {

    private const val PRIVATE_KEY_SIZE = 32

    private val INITIAL_SEED = "Bitcoin seed".encodeToByteArray()

    private val ECDSA_CURVE_ORDER = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    )

    sealed class DerivationError : Exception() {
        object InvalidChildKey : DerivationError()
    }

    override fun deriveFromSeed(seed: ByteArray): Bip32ExtendedKeyPair {
        val hmacResult = seed.hmacSHA512(secret = INITIAL_SEED)

        val privateKey = hmacResult.sliceArray(0 until PRIVATE_KEY_SIZE)
        val chainCode = hmacResult.sliceArray(PRIVATE_KEY_SIZE until hmacResult.size)

        val publicKey = ECDSAUtils.derivePublicKey(privateKey)

        return Bip32ExtendedKeyPair(
            privateKey = privateKey,
            publicKey = publicKey,
            chaincode = chainCode
        )
    }

    override fun deriveChild(
        parent: Bip32ExtendedKeyPair,
        junction: Junction
    ): Bip32ExtendedKeyPair {
        val sourceData = when (junction.type) {
            JunctionType.HARD -> {
                val padding = byteArrayOf(0)

                padding + parent.privateKey + junction.chaincode
            }
            JunctionType.SOFT -> {
                parent.publicKey + junction.chaincode
            }
        }

        val hmacResult = sourceData.hmacSHA512(secret = parent.chaincode)

        val privateKeySourceData = hmacResult.sliceArray(0 until PRIVATE_KEY_SIZE)
        val childChainCode = hmacResult.sliceArray(PRIVATE_KEY_SIZE until hmacResult.size)

        var privateKeyInt = privateKeySourceData.fromUnsignedBytes()

        requireOrException(privateKeyInt < ECDSA_CURVE_ORDER) {
            DerivationError.InvalidChildKey
        }

        privateKeyInt += parent.privateKey.fromUnsignedBytes()
        privateKeyInt %= ECDSA_CURVE_ORDER

        requireOrException(privateKeyInt > BigInteger.ZERO) {
            DerivationError.InvalidChildKey
        }

        var privateKey = privateKeyInt.toUnsignedBytes()

        if (privateKey.size < PRIVATE_KEY_SIZE) {
            val padding = ByteArray(PRIVATE_KEY_SIZE - privateKey.size)

            privateKey = padding + privateKey
        }

        val publicKey = ECDSAUtils.derivePublicKey(privateKey)

        return Bip32ExtendedKeyPair(
            privateKey = privateKey,
            publicKey = publicKey,
            chaincode = childChainCode
        )
    }
}
