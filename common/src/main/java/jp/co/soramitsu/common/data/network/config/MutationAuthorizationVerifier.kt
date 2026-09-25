package jp.co.soramitsu.common.data.network.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.util.Base64

enum class MutationCapability(val wireName: String) {
    DEMETER("demeter"), POLKAMARKT("polkamarkt"), POLKASWAP("polkaswap"),
    POLKASWAP_BRIDGE("polkaswapBridge"), XCM("xcm")
}

data class MutationAuthorizationContext(
    val audience: String,
    val appVersion: Long,
    val policySha256: String,
    val routeManifestSha256: String,
    val trustedKeys: Map<String, ByteArray>,
    val compiledApprovals: Set<MutationCapability>
)

data class VerifiedMutationAuthorization internal constructor(
    val revision: Long,
    val payloadSha256: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val capabilities: Set<MutationCapability>
)

/** Verifies the closed, canonical FWMA1 format. A token is never execution metadata. */
class MutationAuthorizationVerifier {
    fun verify(token: String?, context: MutationAuthorizationContext?, nowSeconds: Long): VerifiedMutationAuthorization? {
        if (token == null || context == null) return null
        return try {
            require(token.length in 1..8192 && token.all { it.code in 33..126 })
            val parts = token.split('.')
            require(parts.size == 4 && parts[0] == "FWMA1")
            val keyId = parts[1]
            require(keyId.matches(Regex("[a-z0-9-]{1,64}")))
            val key = requireNotNull(context.trustedKeys[keyId])
            require(key.size == 32)
            val bytes = decodeCanonicalBase64(parts[2])
            require(bytes.isNotEmpty() && bytes.all { it.toInt() in 32..126 })
            val signature = decodeCanonicalBase64(parts[3])
            require(signature.size == 64)
            val message = "FearlessWallet-MutationAuthorization-v1\n$keyId\n".toByteArray(Charsets.US_ASCII) + bytes
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(key, 0))
            verifier.update(message, 0, message.size)
            require(verifier.verifySignature(signature))

            val payloadText = bytes.toString(Charsets.US_ASCII)
            val payload = JsonParser.parseString(payloadText).asJsonObject
            require(payload.keySet() == PAYLOAD_FIELDS.toSet())
            val canonical = JsonObject()
            for (field in PAYLOAD_FIELDS.dropLast(1)) {
                val value = payload.get(field)
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                canonical.addProperty(field, value.asString)
            }
            require(canonical.get("schema").asString == "1")
            val audience = canonical.get("audience").asString
            require(audience.matches(Regex("[A-Za-z0-9.-]{1,128}")) && audience == context.audience)
            require(canonical.get("environment").asString == "production")
            for ((field, expected) in listOf("policySha256" to context.policySha256, "routeManifestSha256" to context.routeManifestSha256)) {
                val value = canonical.get(field).asString
                require(value.matches(Regex("[0-9a-f]{64}")) && value == expected)
            }
            val minimum = canonicalDecimal(canonical.get("minAppVersion").asString)
            val maximum = canonicalDecimal(canonical.get("maxAppVersion").asString)
            require(minimum > 0 && maximum >= minimum && context.appVersion in minimum..maximum)
            val revision = canonicalDecimal(canonical.get("revision").asString)
            require(revision > 0)
            val issuedAt = canonicalDecimal(canonical.get("issuedAt").asString)
            val expiresAt = canonicalDecimal(canonical.get("expiresAt").asString)
            require(issuedAt in 0..MAX_TIMESTAMP && expiresAt in 0..MAX_TIMESTAMP)
            require(nowSeconds in 0..MAX_TIMESTAMP && issuedAt <= nowSeconds + 60)
            require(issuedAt < expiresAt && nowSeconds < expiresAt && expiresAt - issuedAt <= MAX_LIFETIME_SECONDS)

            val capabilities = payload.getAsJsonObject("capabilities")
            require(capabilities.keySet() == MutationCapability.entries.map { it.wireName }.toSet())
            val canonicalCapabilities = JsonObject()
            val enabled = mutableSetOf<MutationCapability>()
            MutationCapability.entries.forEach { capability ->
                val value = capabilities.get(capability.wireName)
                require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
                canonicalCapabilities.addProperty(capability.wireName, value.asBoolean)
                if (value.asBoolean) enabled += capability
            }
            canonical.add("capabilities", canonicalCapabilities)
            require(canonical.toString() == payloadText)
            VerifiedMutationAuthorization(revision, mutationSha256(bytes), issuedAt, expiresAt, enabled.intersect(context.compiledApprovals))
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeCanonicalBase64(value: String): ByteArray {
        require(value.matches(Regex("[A-Za-z0-9_-]+")))
        return Base64.getUrlDecoder().decode(value).also {
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(it) == value)
        }
    }

    companion object {
        const val MAX_LIFETIME_SECONDS = 900L
        private const val MAX_TIMESTAMP = 253402300799L
        private val PAYLOAD_FIELDS = listOf(
            "schema", "audience", "environment", "minAppVersion", "maxAppVersion", "policySha256", "routeManifestSha256",
            "revision", "issuedAt", "expiresAt", "capabilities"
        )
    }
}

internal fun canonicalDecimal(value: String): Long {
    require(value.matches(Regex("0|[1-9][0-9]{0,18}")))
    return value.toLong()
}

internal fun mutationSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
