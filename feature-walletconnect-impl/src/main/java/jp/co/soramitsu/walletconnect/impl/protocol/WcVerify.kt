package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** The Verify server's P-256 attestation key, as `/v3/public-key` publishes it. */
internal class WcVerifyPublicKey(val x: ByteArray, val y: ByteArray, val expiresAtSeconds: Long) {
    companion object {
        private const val COORDINATE_LENGTH = 32

        /** Reads `{"publicKey": {"kty": "EC", "crv": "P-256", "x": …, "y": …}, "expiresAt": …}`; null when malformed. */
        fun parse(body: String): WcVerifyPublicKey? = runCatching {
            val root = requireNotNull(WcJsonRpcCodec.parseObject(body))
            val jwk = root["publicKey"] as JsonObject
            require(jwk.text("kty") == "EC" && jwk.text("crv") == "P-256")
            val x = WcEncoding.unbase64Url(requireNotNull(jwk.text("x")))
            val y = WcEncoding.unbase64Url(requireNotNull(jwk.text("y")))
            require(x.size == COORDINATE_LENGTH && y.size == COORDINATE_LENGTH)
            WcVerifyPublicKey(x, y, requireNotNull((root["expiresAt"] as? JsonPrimitive)?.longOrNull))
        }.getOrNull()
    }
}

/** Fetches the Verify server's current key; throws when it cannot. */
internal fun interface WcVerifyKeySource {
    suspend fun fetch(): WcVerifyPublicKey
}

/** The claims of a Verify attestation whose ES256 signature checked out. */
internal class WcAttestationClaims(
    val id: String,
    val origin: String,
    val isScam: Boolean?,
    val isVerified: Boolean?,
    val expiresAtSeconds: Long
)

internal object WcAttestationJwt {
    private const val SIGNATURE_LENGTH = 64
    private const val PARTS = 3
    private val curve = ECNamedCurveTable.getByName("P-256")
    private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)

    /** The attestation's claims when [jwt] is an ES256 JWT signed by [key]; null otherwise. */
    fun verify(jwt: String, key: WcVerifyPublicKey): WcAttestationClaims? = runCatching {
        val parts = jwt.split('.')
        require(parts.size == PARTS && decodeObject(parts[0]).text("alg") == "ES256")
        val signature = WcEncoding.unbase64Url(parts[2])
        require(signature.size == SIGNATURE_LENGTH)
        val point = curve.curve.createPoint(BigInteger(1, key.x), BigInteger(1, key.y))
        require(point.isValid)
        val signer = ECDSASigner()
        signer.init(false, ECPublicKeyParameters(point, domain))
        val digest = WcCrypto.sha256((parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII))
        val r = BigInteger(1, signature.copyOfRange(0, SIGNATURE_LENGTH / 2))
        val s = BigInteger(1, signature.copyOfRange(SIGNATURE_LENGTH / 2, SIGNATURE_LENGTH))
        require(signer.verifySignature(digest, r, s))
        val payload = decodeObject(parts[1])
        WcAttestationClaims(
            id = requireNotNull(payload.text("id")),
            origin = requireNotNull(payload.text("origin")),
            isScam = (payload["isScam"] as? JsonPrimitive)?.booleanOrNull,
            isVerified = (payload["isVerified"] as? JsonPrimitive)?.booleanOrNull,
            expiresAtSeconds = requireNotNull((payload["exp"] as? JsonPrimitive)?.longOrNull)
        )
    }.getOrNull()

    private fun decodeObject(part: String): JsonObject =
        requireNotNull(WcJsonRpcCodec.parseObject(String(WcEncoding.unbase64Url(part), Charsets.UTF_8)))
}

/**
 * Verify API v2: checks the attestation the relay delivered with a message against the Verify
 * server's published P-256 key and derives where the dApp really runs. The attestation names the
 * encrypted message it was issued for, so it cannot be replayed onto another message.
 */
internal class WcVerifier(
    private val keySource: WcVerifyKeySource,
    private val clock: WcClock
) {
    private val keyLock = Mutex()

    @Volatile
    private var key: WcVerifyPublicKey? = null

    @Volatile
    private var lastFetchSeconds: Long? = null

    suspend fun resolve(attestation: String?, encryptedMessage: String, claimedUrl: String?): WalletConnectVerifyContext {
        val unknown = WalletConnectVerifyContext.unknown(claimedUrl)
        val claims = attestation?.takeIf { it.isNotEmpty() }?.let { verifiedClaims(it) }
            ?.takeIf { it.id == WcCrypto.hashMessage(encryptedMessage) && it.expiresAtSeconds >= clock.nowSeconds() }
            ?: return unknown
        val scam = claims.isScam == true
        val attestedOrigin = normalizeOrigin(claims.origin)
        // A scam verdict from a valid attestation always counts (naming the site it saw); an origin
        // is trusted only once Verify vouches for it.
        val verifiedOrigin = attestedOrigin?.takeIf { claims.isVerified == true }
            ?: return unknown.copy(isScam = scam, verifiedOrigin = attestedOrigin?.takeIf { scam })
        val claimedOrigin = claimedUrl?.let(::normalizeOrigin)
        val validation = if (verifiedOrigin == claimedOrigin) WalletConnectValidation.VALID else WalletConnectValidation.INVALID
        return WalletConnectVerifyContext(validation, verifiedOrigin, claimedUrl, scam)
    }

    private suspend fun verifiedClaims(attestation: String): WcAttestationClaims? {
        val cached = currentKey(forceRefresh = false) ?: return null
        WcAttestationJwt.verify(attestation, cached)?.let { return it }
        // The server may have rotated its key since it was cached.
        val refreshed = currentKey(forceRefresh = true) ?: return null
        if (refreshed === cached) return null
        return WcAttestationJwt.verify(attestation, refreshed)
    }

    private suspend fun currentKey(forceRefresh: Boolean): WcVerifyPublicKey? = keyLock.withLock {
        val cached = key
        val now = clock.nowSeconds()
        val valid = cached?.takeIf { it.expiresAtSeconds > now }
        if (valid != null && !forceRefresh) return@withLock valid
        // A forced refresh (a signature that did not check out) is rate limited: a dApp sending bad
        // attestations must not make the wallet query the Verify server for every message.
        val lastFetch = lastFetchSeconds
        if (forceRefresh && lastFetch != null && now - lastFetch < REFRESH_INTERVAL_SECONDS) return@withLock valid
        lastFetchSeconds = now
        runCatchingNonCancellation { keySource.fetch() }.getOrNull()
            ?.takeIf { it.expiresAtSeconds > now }
            ?.also { key = it }
            ?: valid
    }

    companion object {
        const val VERIFY_SERVER = "https://verify.walletconnect.org"
        const val PUBLIC_KEY_URL = "$VERIFY_SERVER/v3/public-key"
        private const val REFRESH_INTERVAL_SECONDS = 600L
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443

        /** `scheme://host[:port]` in lower case with default ports dropped, or null when [url] has no host. */
        fun normalizeOrigin(url: String): String? = try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
            if (scheme == null || host.isNullOrEmpty()) {
                null
            } else {
                val port = uri.port.takeIf {
                    it != -1 && !(scheme == "https" && it == HTTPS_PORT) && !(scheme == "http" && it == HTTP_PORT)
                }
                if (port == null) "$scheme://$host" else "$scheme://$host:$port"
            }
        } catch (ignored: URISyntaxException) {
            null
        }
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
