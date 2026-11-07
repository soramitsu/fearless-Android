package jp.co.soramitsu.common.data.network.config.signed

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigDecimal
import java.security.MessageDigest

/** A remote-config file or manifest that failed verification. Nothing from it is used. */
class RemoteConfigVerificationException(message: String) : IllegalStateException(message)

/**
 * The signed remote-config manifest: every configuration file under `remote-config/` with its SHA-256, a sequence that
 * grows with every signing, and an Ed25519 signature over the exact manifest bytes (`scripts/sign-remote-config.py`).
 */
class SignedRemoteConfigManifest private constructor(
    val sequence: Long,
    /** SHA-256 (lowercase hex) of each file, by its path below `remote-config/`. */
    val files: Map<String, String>,
    /** The signed envelope as published, kept to re-verify it after a restart. */
    val envelope: ByteArray
) {
    /** Whether [content] is the signed content of [path]. */
    fun lists(path: String, content: ByteArray): Boolean = files[path] == sha256Hex(content)

    companion object {
        private const val SCHEMA = "fearless-remote-config-manifest/1"
        private const val REPOSITORY = "soramitsu/fearless-Android"
        private const val ALGORITHM = "ed25519"
        private const val SIGNATURE_SIZE = 64
        private val ENVELOPE_FIELDS = setOf("algorithm", "manifest", "signature")
        private val MANIFEST_FIELDS = setOf("files", "keyId", "repository", "schema", "sequence", "signedAt")
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
        private val HEX = Regex("(?:[0-9a-f]{2})+")

        /** Verifies [envelope] against [publicKey]; throws [RemoteConfigVerificationException] unless it is valid. */
        fun verify(
            envelope: ByteArray,
            publicKey: ByteArray = RemoteConfigTrust.publicKey,
            keyId: String = RemoteConfigTrust.KEY_ID
        ): SignedRemoteConfigManifest {
            val outer = parseObject(envelope.toString(Charsets.UTF_8))
            check(outer.keySet() == ENVELOPE_FIELDS) { "Invalid manifest envelope" }
            check(outer.string("algorithm") == ALGORITHM) { "Unsupported manifest signature algorithm" }
            val signed = outer.string("manifest")
            check(signed.all { it.code < ASCII_LIMIT }) { "The signed manifest must be ASCII" }
            val signatureHex = outer.string("signature")
            check(HEX.matches(signatureHex) && signatureHex.length == SIGNATURE_SIZE * 2) { "Invalid manifest signature" }
            val signedBytes = signed.toByteArray(Charsets.US_ASCII)
            check(ed25519Verify(publicKey, signedBytes, signatureHex.hexToBytes())) { "The manifest signature does not verify" }

            val manifest = parseObject(signed)
            check(manifest.keySet() == MANIFEST_FIELDS) { "Invalid manifest fields" }
            check(manifest.string("schema") == SCHEMA) { "Unsupported manifest schema" }
            check(manifest.string("repository") == REPOSITORY) { "The manifest is for another repository" }
            check(manifest.string("keyId") == keyId) { "The manifest names another key" }
            manifest.string("signedAt")
            val sequence = (manifest.get("sequence") as? JsonPrimitive)?.takeIf { it.isNumber }
                ?.let { runCatching { BigDecimal(it.asString).longValueExact() }.getOrNull() }
                ?.takeIf { it >= 1 }
                ?: throw RemoteConfigVerificationException("Invalid manifest sequence")
            val entries = (manifest.get("files") as? JsonObject)?.entrySet()
                ?: throw RemoteConfigVerificationException("Invalid manifest files")
            val files = entries.associate { (path, digest) ->
                check(RemoteConfigTrust.isSignedPath(path)) { "Invalid manifest path" }
                val hex = (digest as? JsonPrimitive)?.takeIf { it.isString }?.asString?.takeIf { SHA256_HEX.matches(it) }
                    ?: throw RemoteConfigVerificationException("Invalid manifest digest")
                path to hex
            }
            return SignedRemoteConfigManifest(sequence, files, envelope.copyOf())
        }

        fun sha256Hex(content: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

        private const val ASCII_LIMIT = 0x80

        private fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
            Ed25519Signer().run {
                init(false, Ed25519PublicKeyParameters(publicKey, 0))
                update(message, 0, message.size)
                verifySignature(signature)
            }
        }.getOrDefault(false)

        private fun parseObject(text: String): JsonObject = runCatching { JsonParser.parseString(text) }.getOrNull() as? JsonObject
            ?: throw RemoteConfigVerificationException("The manifest is not a JSON object")

        private fun JsonObject.string(name: String): String =
            (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: throw RemoteConfigVerificationException("Missing manifest field $name")

        private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        private inline fun check(condition: Boolean, message: () -> String) {
            if (!condition) throw RemoteConfigVerificationException(message())
        }
    }
}
