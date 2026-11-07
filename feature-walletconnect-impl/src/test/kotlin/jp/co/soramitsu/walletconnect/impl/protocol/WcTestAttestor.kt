package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import java.math.BigInteger

/** A stand-in Verify server: a throwaway P-256 key that issues ES256 attestations for messages. */
internal class WcTestAttestor {
    private val curve = ECNamedCurveTable.getByName("P-256")
    private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
    private val secret = BigInteger(1, WcCrypto.randomBytes(32)).mod(curve.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
    private val point = curve.g.multiply(secret).normalize()

    val publicKey = WcVerifyPublicKey(point.affineXCoord.encoded, point.affineYCoord.encoded, expiresAtSeconds = 4_000_000_000)

    /** An attestation for the encrypted [message], as the relay delivers it next to the message. */
    fun attest(message: String, origin: String, isScam: Boolean = false, isVerified: Boolean = true, expiresAt: Long = 4_000_000_000): String {
        val header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "JWT")
        }
        val payload = buildJsonObject {
            put("exp", expiresAt)
            put("id", WcCrypto.hashMessage(message))
            put("origin", origin)
            put("isScam", isScam)
            put("isVerified", isVerified)
        }
        val input = WcEncoding.base64Url(header.toString().toByteArray()) + "." + WcEncoding.base64Url(payload.toString().toByteArray())
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(secret, domain))
        val (r, s) = signer.generateSignature(WcCrypto.sha256(input.toByteArray())).let { it[0] to it[1] }
        return input + "." + WcEncoding.base64Url(fixed(r) + fixed(s))
    }

    private fun fixed(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it.toInt() == 0 }.toByteArray()
        return ByteArray(32 - bytes.size) + bytes
    }
}
