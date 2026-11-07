package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * The relay client identity: an Ed25519 key whose `did:key` names this wallet to the relay. It
 * signs only the relay's client-auth JWT; it is no wallet key and never signs anything for a dApp.
 */
internal class WcClientIdentity(seed: ByteArray) {
    private val privateKey: Ed25519PrivateKeyParameters

    init {
        require(seed.size == SEED_LENGTH) { "Invalid client seed" }
        privateKey = Ed25519PrivateKeyParameters(seed, 0)
    }

    val publicKey: ByteArray = privateKey.generatePublicKey().encoded

    /** `did:key:z6Mk…`: multicodec ed25519-pub (0xed 0x01) and the key, base58btc. */
    val clientId: String = didKey(publicKey)

    /**
     * The client-auth JWT the relay requires on connect: EdDSA over
     * `base64url(header).base64url(payload)` with `iss` = [clientId], valid for [ttlSeconds].
     */
    fun signJwt(audience: String, subject: String, issuedAtSeconds: Long, ttlSeconds: Long = JWT_TTL_SECONDS): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "JWT")
        }
        val payload = buildJsonObject {
            put("iss", clientId)
            put("sub", subject)
            put("aud", audience)
            put("iat", issuedAtSeconds)
            put("exp", issuedAtSeconds + ttlSeconds)
        }
        val signingInput = WcEncoding.base64Url(header.toString().toByteArray(Charsets.UTF_8)) + "." +
            WcEncoding.base64Url(payload.toString().toByteArray(Charsets.UTF_8))
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val data = signingInput.toByteArray(Charsets.US_ASCII)
        signer.update(data, 0, data.size)
        return signingInput + "." + WcEncoding.base64Url(signer.generateSignature())
    }

    companion object {
        const val SEED_LENGTH = 32
        const val JWT_TTL_SECONDS = 86_400L
        private val ED25519_MULTICODEC = byteArrayOf(0xed.toByte(), 0x01)

        fun didKey(publicKey: ByteArray): String = "did:key:z" + WcEncoding.base58(ED25519_MULTICODEC + publicKey)
    }
}
