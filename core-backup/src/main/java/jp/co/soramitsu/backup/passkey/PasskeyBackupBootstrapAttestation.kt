package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64

/** The signed wallet-proof inputs used by the owner authority to derive its attestation nonce. */
class PasskeyBackupBootstrapWalletProofBinding private constructor(val requestHash: String) {
    override fun toString(): String = "PasskeyBackupBootstrapWalletProofBinding(redacted)"

    enum class Scheme(val wireValue: String) { ED25519("ed25519"), SECP256K1("secp256k1") }

    companion object {
        private val WALLET_DOMAIN = "FP_OWNER_BOOTSTRAP_WALLET_V1\u0000".toByteArray(Charsets.US_ASCII)
        private val APP_DOMAIN = "FP_OWNER_BOOTSTRAP_APP_V1\u0000".toByteArray(Charsets.US_ASCII)
        private const val MAX_WALLET_MESSAGE_BYTES = 8_192
        private const val SIGNATURE_BYTES = 64
        private const val ED25519_KEY_BYTES = 32
        private const val SECP256K1_COMPRESSED_KEY_BYTES = 33
        private const val SECP256K1_EVEN_PREFIX = 2
        private const val SECP256K1_ODD_PREFIX = 3

        /** Mirrors the server's SHA-256 over domain-separated, length-prefixed signed-proof fields. */
        fun fromSignedWalletProof(
            walletMessage: ByteArray,
            scheme: Scheme,
            normalizedPublicKey: ByteArray,
            signature: ByteArray
        ): PasskeyBackupBootstrapWalletProofBinding {
            require(
                walletMessage.size in WALLET_DOMAIN.size + 1..MAX_WALLET_MESSAGE_BYTES &&
                    walletMessage.copyOfRange(0, WALLET_DOMAIN.size).contentEquals(WALLET_DOMAIN)
            ) { "Invalid owner-bootstrap wallet message" }
            require(signature.size == SIGNATURE_BYTES) { "Invalid wallet-proof signature length" }
            when (scheme) {
                Scheme.ED25519 -> require(normalizedPublicKey.size == ED25519_KEY_BYTES) {
                    "Invalid normalized Ed25519 wallet key"
                }
                Scheme.SECP256K1 -> require(
                    normalizedPublicKey.size == SECP256K1_COMPRESSED_KEY_BYTES &&
                        (
                            normalizedPublicKey[0] == SECP256K1_EVEN_PREFIX.toByte() ||
                                normalizedPublicKey[0] == SECP256K1_ODD_PREFIX.toByte()
                        )
                ) { "Invalid normalized secp256k1 wallet key" }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(
                ByteArrayOutputStream().also { output ->
                    DataOutputStream(output).use { data ->
                        data.write(APP_DOMAIN)
                        data.writeField(walletMessage)
                        data.writeField(scheme.wireValue.toByteArray(Charsets.US_ASCII))
                        data.writeField(normalizedPublicKey)
                        data.writeField(signature)
                    }
                }.toByteArray()
            )
            return PasskeyBackupBootstrapWalletProofBinding(
                Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            )
        }

        private fun DataOutputStream.writeField(bytes: ByteArray) {
            writeInt(bytes.size)
            write(bytes)
        }
    }
}

/**
 * The app attestation the owner authority requires before it creates a first owner. 4.3.2 removed
 * Google Play Integrity, a closed SDK, and no open attestation exists yet, so the only provider is
 * [UnavailablePasskeyBackupBootstrapAttestationProvider] and passkey backup stays compiled off
 * (PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED). Enabling it needs an open attestation design
 * that the owner authority accepts in place of `play-integrity`.
 */
class PasskeyBackupBootstrapAttestation(
    val kind: String,
    val token: String,
    val requestHash: String
) {
    init {
        require(KIND_PATTERN.matches(kind)) { "Invalid app attestation kind" }
        require(token.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS && TOKEN_PATTERN.matches(token)) {
            "Invalid app attestation token"
        }
        require(REQUEST_HASH_PATTERN.matches(requestHash)) { "Invalid app attestation request hash" }
    }

    /** Exact public transport shape of the owner authority's `appAttestation` field. */
    fun serverAttestationJson(): String = JsonObject().apply {
        addProperty("kind", kind)
        addProperty("token", token)
    }.toString()

    override fun toString(): String = "PasskeyBackupBootstrapAttestation(kind=$kind, redacted)"

    private companion object {
        const val MIN_TOKEN_CHARS = 32
        const val MAX_TOKEN_CHARS = 32_768
        val KIND_PATTERN = Regex("^[a-z][a-z0-9-]{0,31}$")
        val TOKEN_PATTERN = Regex("^[A-Za-z0-9._-]+$")
        val REQUEST_HASH_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

/** Attests this app instance for exactly one signed wallet proof, bound by its request hash. */
fun interface PasskeyBackupBootstrapAttestationProvider {
    suspend fun attest(binding: PasskeyBackupBootstrapWalletProofBinding): PasskeyBackupBootstrapAttestation
}

/** The production provider until an open attestation design exists; it never contacts anything. */
class UnavailablePasskeyBackupBootstrapAttestationProvider : PasskeyBackupBootstrapAttestationProvider {
    override suspend fun attest(binding: PasskeyBackupBootstrapWalletProofBinding): PasskeyBackupBootstrapAttestation {
        error("Passkey backup needs an open app attestation design; Play Integrity is not part of the app")
    }
}
