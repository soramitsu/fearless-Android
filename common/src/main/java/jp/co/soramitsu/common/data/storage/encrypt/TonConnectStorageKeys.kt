package jp.co.soramitsu.common.data.storage.encrypt

import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex

/**
 * Dependency-neutral TON Connect preference-key contract shared by wallet
 * deletion and the TON Connect repository.
 */
object TonConnectStorageKeys {

    const val MUTATION_JOURNAL_KEY = "TON_CONNECT_MUTATION_JOURNAL_V1"
    const val MAX_CONNECTIONS_PER_WALLET = 256
    const val MAX_URL_CHARS = 4_096
    const val MAX_URL_BYTES = MAX_URL_CHARS * 4

    private const val SCOPED_KEY_PREFIX = "TON_CONNECT_SCOPED_V1_"
    private const val LEGACY_KEY_PREFIX = "TON_CONNECT_"
    private const val HASH_HEX_CHARS = 64
    private const val LENGTH_PREFIX_BYTES = 4
    private const val LONG_BYTES = 8
    private val DOMAIN = "fearless-ton-connect-row-secret:v1"
        .toByteArray(Charsets.UTF_8)
    private val VALID_SOURCES = setOf("QR", "WEB")

    fun scoped(
        metaId: Long,
        url: String,
        source: String
    ): String {
        require(metaId > 0L) {
            "A TON Connect secret identity requires a positive wallet id"
        }
        require(url.isNotBlank() && url.length <= MAX_URL_CHARS) {
            "A TON Connect secret identity contains an invalid URL"
        }
        val encodedUrl = strictUtf8(url)
        require(encodedUrl.size <= MAX_URL_BYTES) {
            "A TON Connect secret identity URL is too large"
        }
        require(source in VALID_SOURCES) {
            "A TON Connect secret identity has an invalid source"
        }

        val encodedSource = source.toByteArray(Charsets.US_ASCII)
        val encodedMetaId = ByteArray(LONG_BYTES) { index ->
            (
                metaId ushr
                    ((LONG_BYTES - index - 1) * Byte.SIZE_BITS)
                ).toByte()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        updateLengthPrefixed(digest, DOMAIN)
        updateLengthPrefixed(digest, encodedMetaId)
        updateLengthPrefixed(digest, encodedUrl)
        updateLengthPrefixed(digest, encodedSource)

        return scopedPrefix(metaId) + digest.digest().toLowerHex()
    }

    fun scopedPrefix(metaId: Long): String {
        require(metaId > 0L) {
            "A TON Connect scoped-key prefix requires a positive wallet id"
        }
        return "$SCOPED_KEY_PREFIX${metaId}_"
    }

    fun legacy(clientId: String): String {
        requireValidClientId(clientId)
        return LEGACY_KEY_PREFIX + clientId
    }

    fun requireValidClientId(clientId: String) {
        val validLength = clientId.length == WEB_CLIENT_ID_HEX_CHARS ||
            clientId.length == QR_CLIENT_ID_HEX_CHARS
        require(validLength && clientId.all(::isAsciiHexDigit)) {
            "A TON Connect client id must be 32 or 64 hexadecimal characters"
        }
    }

    fun isScopedKey(key: String): Boolean {
        if (!key.startsWith(SCOPED_KEY_PREFIX)) return false
        val suffix = key.removePrefix(SCOPED_KEY_PREFIX)
        val separatorIndex = suffix.indexOf('_')
        if (separatorIndex <= 0) return false
        val encodedMetaId = suffix.substring(0, separatorIndex)
        val metaId = encodedMetaId.toLongOrNull()
        if (
            metaId == null ||
            metaId <= 0L ||
            metaId.toString() != encodedMetaId
        ) {
            return false
        }
        val digest = suffix.substring(separatorIndex + 1)
        return digest.length == HASH_HEX_CHARS &&
            digest.all { it in '0'..'9' || it in 'a'..'f' }
    }

    fun isScopedKeyForMeta(key: String, metaId: Long): Boolean {
        return metaId > 0L &&
            key.startsWith(scopedPrefix(metaId)) &&
            isScopedKey(key)
    }

    fun isLegacyKey(key: String): Boolean {
        if (!key.startsWith(LEGACY_KEY_PREFIX)) return false
        val clientId = key.removePrefix(LEGACY_KEY_PREFIX)
        return runCatching { requireValidClientId(clientId) }.isSuccess
    }

    private fun updateLengthPrefixed(
        digest: MessageDigest,
        value: ByteArray
    ) {
        val length = value.size
        for (
            shift in (LENGTH_PREFIX_BYTES - 1) * Byte.SIZE_BITS
                downTo 0 step Byte.SIZE_BITS
        ) {
            digest.update((length ushr shift).toByte())
        }
        digest.update(value)
    }

    private fun ByteArray.toLowerHex(): String {
        return joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff)
                .toString(HEX_RADIX)
                .padStart(2, '0')
        }
    }

    private fun isAsciiHexDigit(character: Char): Boolean {
        return character in '0'..'9' ||
            character in 'a'..'f' ||
            character in 'A'..'F'
    }

    private fun strictUtf8(value: String): ByteArray {
        return try {
            val buffer = Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
            ByteArray(buffer.remaining()).also(buffer::get)
        } catch (failure: CharacterCodingException) {
            throw IllegalArgumentException(
                "A TON Connect URL contains malformed Unicode",
                failure
            )
        }
    }

    private const val WEB_CLIENT_ID_HEX_CHARS = 32
    private const val QR_CLIENT_ID_HEX_CHARS = 64
    private const val HEX_RADIX = 16
}

/** One process-wide lock for every Room + encrypted-preference mutation. */
object WalletCrossStoreMutationMutex {
    val instance = Mutex()
}
