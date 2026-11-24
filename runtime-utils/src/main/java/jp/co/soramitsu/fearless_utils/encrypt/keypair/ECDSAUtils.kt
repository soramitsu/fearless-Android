package jp.co.soramitsu.fearless_utils.encrypt.keypair

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.Sign
import java.math.BigInteger

object ECDSAUtils {

    private val secp256k1Curve by lazy { CustomNamedCurves.getByName("secp256k1").curve }

    fun compressedPublicKeyFromPrivate(privKey: BigInteger): ByteArray {
        val point = Sign.publicPointFromPrivate(privKey)
        return point.getEncoded(true)
    }

    fun decompressedAsInt(compressedKey: ByteArray): BigInteger {
        val decompressedArray = decompressed(compressedKey)

        return Hex.toHexString(byteArrayOf(0x00) + decompressedArray).toBigInteger(16)
    }

    fun decompressed(compressedKey: ByteArray): ByteArray {
        val point = secp256k1Curve.decodePoint(compressedKey)
        val x: ByteArray = point.xCoord.encoded
        val y: ByteArray = point.yCoord.encoded

        return x + y
    }
}

fun ECDSAUtils.derivePublicKey(privateKeyOrSeed: ByteArray): ByteArray {
    val privateKeyInt = BigInteger(privateKeyOrSeed.toHexString(), 16)

    return compressedPublicKeyFromPrivate(privateKeyInt)
}
