package jp.co.soramitsu.common.data.storage.envelope

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The inner layer of a wallet secret value (4.3.2).
 *
 * A sealed value is `fse1:` followed by unpadded base64url of
 * `header ‖ nonce ‖ AES-256-GCM(SK, body)`. It is the plaintext that the
 * outer [jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences]
 * layer encrypts under the device key, which stays exactly as it was.
 *
 * The header is readable without SK and is authenticated as associated data
 * once SK opens the value: format version, the 16-byte SK id, the scope, the
 * key name the value was sealed under, and its public identity (public key or
 * account id bytes, empty when the payload could not be parsed). The recorded
 * key name is the original active key: an exact-ciphertext move to a
 * quarantine or archive key, or the legacy ACCESS payload's move to the
 * Ethereum quarantine, keeps the envelope byte for byte, so it still opens
 * there. The body is the exact old plaintext, unchanged.
 */
object WalletSecretEnvelope {

    const val PREFIX = "fse1:"
    const val FORMAT_VERSION = 1
    const val KEY_ID_BYTES = 16
    const val KEY_BYTES = 32

    /**
     * The longest envelope a reader parses. A 1 MiB plaintext seals to about
     * 1.4 million characters, and an envelope this long still encrypts to
     * less than the outer layer's 2,097,152-character read bound.
     */
    const val MAX_ENVELOPE_CHARS = 1_500_000

    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val MAX_KEY_NAME_BYTES = 1_024
    private const val MAX_IDENTITY_BYTES = 256
    private const val AAD_DOMAIN = "fearless-wallet-secret-envelope:v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /** True for every value that starts like an envelope, well-formed or not. */
    fun isSealed(value: String?): Boolean = value?.startsWith(PREFIX) == true

    class Header(
        keyId: ByteArray,
        val scope: WalletSecretEnvelopeScope,
        val keyName: String,
        publicIdentity: ByteArray,
        val version: Int = FORMAT_VERSION
    ) {
        private val keyIdBytes = keyId.copyOf()
        private val identityBytes = publicIdentity.copyOf()

        init {
            require(version == FORMAT_VERSION) { "Unsupported wallet secret envelope version" }
            require(keyIdBytes.size == KEY_ID_BYTES) { "An envelope key id has 16 bytes" }
            require(keyName.isNotEmpty() && keyName.encodeToByteArray().size <= MAX_KEY_NAME_BYTES) {
                "An envelope key name is empty or too long"
            }
            require(identityBytes.size <= MAX_IDENTITY_BYTES) { "An envelope identity is too long" }
        }

        val keyId: ByteArray get() = keyIdBytes.copyOf()
        val publicIdentity: ByteArray get() = identityBytes.copyOf()

        fun keyIdMatches(other: ByteArray): Boolean = MessageDigest.isEqual(keyIdBytes, other)

        internal fun encode(): ByteArray = ByteArrayOutputStream().apply {
            val name = keyName.encodeToByteArray()
            write(version)
            write(keyIdBytes)
            write(scope.code)
            writeUnsignedShort(name.size)
            write(name)
            writeUnsignedShort(identityBytes.size)
            write(identityBytes)
        }.toByteArray()

        override fun toString(): String = "WalletSecretEnvelope.Header(scope=$scope)"
    }

    /** What an opened envelope held. [body] is a wallet secret: never log or persist it. */
    class Opened(val header: Header, val body: String) {
        override fun toString(): String = "WalletSecretEnvelope.Opened(<redacted>)"
    }

    fun seal(key: ByteArray, header: Header, body: String, random: SecureRandom): String {
        require(key.size == KEY_BYTES) { "SK has 32 bytes" }
        require(!isSealed(body)) { "A sealed value is never sealed again" }
        val headerBytes = header.encode()
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val plain = body.encodeToByteArray()
        try {
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, nonce, headerBytes).doFinal(plain)
            val payload = ByteArray(headerBytes.size + NONCE_BYTES + sealed.size)
            headerBytes.copyInto(payload, 0)
            nonce.copyInto(payload, headerBytes.size)
            sealed.copyInto(payload, headerBytes.size + NONCE_BYTES)
            return PREFIX + encoder.encodeToString(payload)
        } finally {
            plain.fill(0)
        }
    }

    /** Parses the header only; SK is not needed and nothing is authenticated yet. */
    fun readHeader(value: String): Header = parse(value).header

    /**
     * Opens [value] with [key]. A wrong key or any changed byte fails with
     * [WalletSecretEnvelopeAuthenticationException]; a value that is not a
     * well-formed envelope fails with [WalletSecretEnvelopeFormatException].
     */
    fun open(key: ByteArray, value: String): Opened {
        require(key.size == KEY_BYTES) { "SK has 32 bytes" }
        val parsed = parse(value)
        val plain = try {
            cipher(Cipher.DECRYPT_MODE, key, parsed.nonce, parsed.headerBytes).doFinal(parsed.sealed)
        } catch (failure: AEADBadTagException) {
            throw WalletSecretEnvelopeAuthenticationException(failure)
        }
        try {
            val body = plain.decodeToString(throwOnInvalidSequence = true)
            return Opened(parsed.header, body)
        } catch (failure: CharacterCodingException) {
            throw WalletSecretEnvelopeFormatException("A sealed wallet secret body is not UTF-8", failure)
        } finally {
            plain.fill(0)
        }
    }

    private class Parsed(val header: Header, val headerBytes: ByteArray, val nonce: ByteArray, val sealed: ByteArray)

    private fun parse(value: String): Parsed {
        if (!isSealed(value)) throw WalletSecretEnvelopeFormatException("Not a sealed wallet secret")
        if (value.length > MAX_ENVELOPE_CHARS) throw WalletSecretEnvelopeFormatException("A sealed wallet secret is too large")
        val encoded = value.substring(PREFIX.length)
        if (encoded.isEmpty() || encoded.any { it == '=' || it == '+' || it == '/' }) {
            throw WalletSecretEnvelopeFormatException("A sealed wallet secret is not unpadded base64url")
        }
        val payload = try {
            decoder.decode(encoded)
        } catch (failure: IllegalArgumentException) {
            throw WalletSecretEnvelopeFormatException("A sealed wallet secret is not base64url", failure)
        }
        val reader = Reader(payload)
        val version = reader.byte()
        if (version != FORMAT_VERSION) throw WalletSecretEnvelopeFormatException("Unsupported wallet secret envelope version")
        val keyId = reader.bytes(KEY_ID_BYTES)
        val scope = WalletSecretEnvelopeScope.fromCode(reader.byte())
            ?: throw WalletSecretEnvelopeFormatException("Unknown wallet secret envelope scope")
        val nameLength = reader.unsignedShort()
        if (nameLength == 0 || nameLength > MAX_KEY_NAME_BYTES) throw WalletSecretEnvelopeFormatException("Bad key name")
        val name = try {
            reader.bytes(nameLength).decodeToString(throwOnInvalidSequence = true)
        } catch (failure: CharacterCodingException) {
            throw WalletSecretEnvelopeFormatException("A sealed key name is not UTF-8", failure)
        }
        val identityLength = reader.unsignedShort()
        if (identityLength > MAX_IDENTITY_BYTES) throw WalletSecretEnvelopeFormatException("Bad public identity")
        val identity = reader.bytes(identityLength)
        val headerEnd = reader.position
        val nonce = reader.bytes(NONCE_BYTES)
        val sealed = reader.rest()
        if (sealed.size < TAG_BYTES) throw WalletSecretEnvelopeFormatException("A sealed wallet secret is truncated")
        val header = Header(keyId, scope, name, identity, version)
        return Parsed(header, payload.copyOfRange(0, headerEnd), nonce, sealed)
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, headerBytes: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(AAD_DOMAIN.encodeToByteArray())
            updateAAD(headerBytes)
        }

    private fun ByteArrayOutputStream.writeUnsignedShort(value: Int) {
        require(value in 0..0xFFFF)
        write(value ushr 8)
        write(value and 0xFF)
    }

    private class Reader(private val bytes: ByteArray) {
        var position = 0
            private set

        fun byte(): Int {
            need(1)
            return bytes[position++].toInt() and 0xFF
        }

        fun unsignedShort(): Int = (byte() shl 8) or byte()

        fun bytes(count: Int): ByteArray {
            need(count)
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        fun rest(): ByteArray = bytes.copyOfRange(position, bytes.size).also { position = bytes.size }

        private fun need(count: Int) {
            if (count < 0 || bytes.size - position < count) {
                throw WalletSecretEnvelopeFormatException("A sealed wallet secret is truncated")
            }
        }
    }
}

/** Which secret a sealed value holds. Codes are persisted: never renumber. */
enum class WalletSecretEnvelopeScope(val code: Int) {
    /** `<id>:SUBSTRATE_SECRETS`. */
    SUBSTRATE_ROOT(1),

    /** `<id>:ETHEREUM_SECRETS`. */
    ETHEREUM_ROOT(2),

    /** `<id>:TON_SECRETS`. */
    TON_ROOT(3),

    /** The pre-V3 `<id>:ACCESS_SECRETS` payload (Substrate, Ethereum and TON keypairs). */
    LEGACY_META_ACCOUNT(4),

    /** `<id>:<accountIdHex>:ACCESS_SECRETS`, a per-network account with its own key. */
    CHAIN_ACCOUNT(5),

    /** 1.x `security_source_<address>`. */
    LEGACY_V1(6),

    /** 0.4 `private_<address>` signing data. */
    LEGACY_V04_SIGNING(7),

    /** 0.4 `seed_`, `entropy_` and `derivation_` values. */
    LEGACY_V04_TEXT(8);

    companion object {
        fun fromCode(code: Int): WalletSecretEnvelopeScope? = entries.firstOrNull { it.code == code }
    }
}

/** A value starting with the envelope prefix that is not a well-formed envelope. */
class WalletSecretEnvelopeFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** SK did not authenticate the envelope: another SK sealed it, or a byte changed. */
class WalletSecretEnvelopeAuthenticationException(cause: Throwable? = null) :
    IllegalStateException("A sealed wallet secret did not authenticate", cause)
