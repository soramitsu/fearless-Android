package jp.co.soramitsu.backup.passkey

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Supplied only from an audited Google Cloud/Play configuration; there is no built-in project. */
class PasskeyBackupPlayIntegrityCloudProject(val number: Long) {
    init {
        require(number > 0) { "A configured Play Integrity Cloud project number is required" }
    }
}

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

/** An opaque, untrusted Google token; only the server may decode and admit its verdict. */
class PasskeyBackupPlayIntegrityBootstrapToken internal constructor(
    val token: String,
    val requestHash: String
) {
    init {
        require(token.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS && TOKEN_PATTERN.matches(token)) {
            "Invalid Play Integrity token"
        }
        require(REQUEST_HASH_PATTERN.matches(requestHash)) { "Invalid Play Integrity request hash" }
    }

    /** Exact public transport shape accepted by the owner's Android attestation field. */
    fun serverAttestationJson(): String = JsonObject().apply {
        addProperty("kind", "play-integrity")
        addProperty("token", token)
    }.toString()

    override fun toString(): String = "PasskeyBackupPlayIntegrityBootstrapToken(redacted)"

    private companion object {
        const val MIN_TOKEN_CHARS = 32
        const val MAX_TOKEN_CHARS = 32_768
        val TOKEN_PATTERN = Regex("^[A-Za-z0-9._-]+$")
        val REQUEST_HASH_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

/** A test seam around Google's two-step Standard Integrity API. */
interface PasskeyBackupStandardIntegrityGateway {
    suspend fun prepare(cloudProjectNumber: Long): PreparedProvider

    fun interface PreparedProvider {
        suspend fun request(requestHash: String): String
    }
}

/** Native Standard API adapter; no verdict parsing or server authorization occurs on-device. */
class SystemPasskeyBackupStandardIntegrityGateway(context: Context) : PasskeyBackupStandardIntegrityGateway {
    private val applicationContext = context.applicationContext
    private val manager by lazy { IntegrityManagerFactory.createStandard(applicationContext) }

    override suspend fun prepare(cloudProjectNumber: Long): PasskeyBackupStandardIntegrityGateway.PreparedProvider {
        val request = StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        val provider = manager.prepareIntegrityToken(request).awaitWithoutDetails()
        return PasskeyBackupStandardIntegrityGateway.PreparedProvider { requestHash ->
            provider.request(
                StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                    .setRequestHash(requestHash)
                    .build()
            ).awaitWithoutDetails().token()
        }
    }

    private suspend fun <T : Any> Task<T>.awaitWithoutDetails(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { completed ->
            if (!continuation.isActive) return@addOnCompleteListener
            val value = if (completed.isSuccessful) completed.result else null
            if (value != null) {
                continuation.resume(value)
            } else {
                continuation.resumeWithException(IllegalStateException("Play Integrity unavailable"))
            }
        }
    }
}

/**
 * Requests one first-owner token for the exact already signed wallet proof. This is intentionally
 * unwired and release-disabled until the owner authority, audited Cloud project and device gates pass.
 */
class PasskeyBackupPlayIntegrityBootstrapRequester internal constructor(
    private val cloudProject: PasskeyBackupPlayIntegrityCloudProject,
    private val gateway: PasskeyBackupStandardIntegrityGateway,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    private val prepareMutex = Mutex()
    private var prepared: PasskeyBackupStandardIntegrityGateway.PreparedProvider? = null

    suspend fun warmUp() {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        preparedProvider()
    }

    suspend fun requestToken(
        binding: PasskeyBackupBootstrapWalletProofBinding
    ): PasskeyBackupPlayIntegrityBootstrapToken {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val provider = preparedProvider()
        val token = try {
            provider.request(binding.requestHash)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            discard(provider)
            error("Play Integrity token unavailable")
        }
        return try {
            PasskeyBackupPlayIntegrityBootstrapToken(token, binding.requestHash)
        } catch (_: IllegalArgumentException) {
            discard(provider)
            error("Play Integrity token unavailable")
        }
    }

    private suspend fun preparedProvider(): PasskeyBackupStandardIntegrityGateway.PreparedProvider =
        prepareMutex.withLock {
            prepared ?: try {
                gateway.prepare(cloudProject.number).also { prepared = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                error("Play Integrity preparation unavailable")
            }
        }

    private suspend fun discard(provider: PasskeyBackupStandardIntegrityGateway.PreparedProvider) {
        prepareMutex.withLock {
            if (prepared === provider) prepared = null
        }
    }

    companion object {
        /** Production construction has no test override and no default Cloud project number. */
        fun create(context: Context, cloudProject: PasskeyBackupPlayIntegrityCloudProject) =
            PasskeyBackupPlayIntegrityBootstrapRequester(
                cloudProject, SystemPasskeyBackupStandardIntegrityGateway(context)
            )
    }
}
