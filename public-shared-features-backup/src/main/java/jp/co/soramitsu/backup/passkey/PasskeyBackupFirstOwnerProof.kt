package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64

private const val WALLET_SIGNATURE_BYTES = 64
private const val ED25519_PUBLIC_KEY_BYTES = 32
private const val SECP256K1_PUBLIC_KEY_BYTES = 33
private const val SECP256K1_EVEN_PREFIX = 2
private const val SECP256K1_ODD_PREFIX = 3

/** The application must authorize the local wallet and prove all original keys before bootstrap. */
interface PasskeyBackupFirstOwnerWalletAuthorizer {
    suspend fun authorizeAndVerifyOriginalKeys(
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupFirstOwnerAuthorizedWallet

    /** Re-derive the original wallet identity and sign the exact message after native UI returns. */
    suspend fun signOriginalWalletMessage(
        message: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupFirstOwnerSignedProof
}

/** The canonical original signing key selected during local authorization. */
class PasskeyBackupFirstOwnerAuthorizedWallet(
    val originalWalletEvidence: PasskeyBackupLocalWalletEvidence,
    val scheme: PasskeyBackupBootstrapWalletProofBinding.Scheme,
    normalizedPublicKey: ByteArray
) {
    private val keyBytes = normalizedPublicKey.copyOf()
    val normalizedPublicKey: ByteArray get() = keyBytes.copyOf()

    init {
        requireNormalizedKey(scheme, keyBytes)
    }

    override fun toString(): String = "PasskeyBackupFirstOwnerAuthorizedWallet(redacted)"
}

/** Public proof only. The private signing key and exported wallet material remain in the app. */
class PasskeyBackupFirstOwnerSignedProof(
    val scheme: PasskeyBackupBootstrapWalletProofBinding.Scheme,
    normalizedPublicKey: ByteArray,
    signature: ByteArray,
    val originalWalletEvidence: PasskeyBackupLocalWalletEvidence
) {
    private val keyBytes = normalizedPublicKey.copyOf()
    private val signatureBytes = signature.copyOf()
    val normalizedPublicKey: ByteArray get() = keyBytes.copyOf()
    val signature: ByteArray get() = signatureBytes.copyOf()

    init {
        require(signatureBytes.size == WALLET_SIGNATURE_BYTES) { "Invalid first-owner wallet signature length" }
        requireNormalizedKey(scheme, keyBytes)
    }

    fun serverJson(): JsonObject = JsonObject().apply {
        addProperty("scheme", scheme.wireValue)
        addProperty("publicKey", Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes))
        addProperty("signature", Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes))
    }

    override fun toString(): String = "PasskeyBackupFirstOwnerSignedProof(redacted)"
}

private fun requireNormalizedKey(scheme: PasskeyBackupBootstrapWalletProofBinding.Scheme, keyBytes: ByteArray) {
    when (scheme) {
        PasskeyBackupBootstrapWalletProofBinding.Scheme.ED25519 ->
            require(keyBytes.size == ED25519_PUBLIC_KEY_BYTES) { "Invalid first-owner Ed25519 key" }
        PasskeyBackupBootstrapWalletProofBinding.Scheme.SECP256K1 ->
            require(
                keyBytes.size == SECP256K1_PUBLIC_KEY_BYTES &&
                    keyBytes[0].toInt() in SECP256K1_EVEN_PREFIX..SECP256K1_ODD_PREFIX
            ) { "Invalid first-owner secp256k1 key" }
    }
}

/** Exact v1 positional commitment used by the owner service for first WebAuthn registration. */
internal object PasskeyBackupFirstOwnerProof {
    private val DOMAIN = "FP_OWNER_BOOTSTRAP_WALLET_V1\u0000".toByteArray(Charsets.US_ASCII)
    private val FIELDS = setOf("id", "rawId", "type", "response", "clientExtensionResults")
    private val RESPONSE_FIELDS = setOf("clientDataJSON", "attestationObject")

    fun message(challenge: PasskeyBackupFirstOwnerChallenge, serverCredentialJson: String): ByteArray {
        val credential = JsonParser.parseString(serverCredentialJson).asJsonObject
        require(credential.keySet() == FIELDS && credential.get("type").asString == "public-key") {
            "Invalid sanitized bootstrap credential"
        }
        val id = credential.get("id").asString
        requireCredentialId(id)
        require(credential.get("rawId").asString == id) { "Bootstrap credential ID mismatch" }
        val extensions = credential.getAsJsonObject("clientExtensionResults")
        require(extensions.keySet().all { it == "credProps" }) { "Bootstrap credential has private extensions" }
        val resident = extensions.getAsJsonObject("credProps")?.let {
            require(it.keySet() == setOf("rk") && it.get("rk").asJsonPrimitive.isBoolean)
            it.get("rk").asBoolean
        }
        require(resident != false) { "Bootstrap credential is not discoverable" }
        val response = credential.getAsJsonObject("response")
        require(response.keySet() == RESPONSE_FIELDS) { "Unexpected bootstrap credential response" }
        val commitmentArray = JsonArray().apply {
            add(id)
            add(id)
            add("public-key")
            add(null as String?)
            add(resident)
            add(response.get("clientDataJSON").asString)
            add(response.get("attestationObject").asString)
            add(null as String?)
            add(null as String?)
            add(null as String?)
            add(null as String?)
        }
        val commitment = MessageDigest.getInstance("SHA-256")
            .digest(commitmentArray.toString().toByteArray(Charsets.UTF_8))
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.write(DOMAIN)
                data.writeField(challenge.ceremonyId.toByteArray(Charsets.UTF_8))
                data.writeField(challenge.challenge)
                data.writeField(PasskeyBackupContract.PASSKEY_RP_ID.toByteArray(Charsets.US_ASCII))
                data.writeField("android".toByteArray(Charsets.US_ASCII))
                data.writeField(challenge.subject.toByteArray(Charsets.UTF_8))
                data.writeField(challenge.namespace.toByteArray(Charsets.UTF_8))
                data.writeField(challenge.userHandle)
                data.writeField(commitment)
            }
        }.toByteArray()
    }

    private fun DataOutputStream.writeField(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}

internal class PasskeyBackupFirstOwnerChallenge(
    val ceremonyId: String,
    val challenge: ByteArray,
    val subject: String,
    val namespace: String,
    val userHandle: ByteArray,
    val expiresAt: Long
)
