package jp.co.soramitsu.common.data.storage.envelope

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.storage.Preferences
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.util.encoders.Hex

/**
 * The records that hold SK, in plain preferences outside the device key, so a
 * fresh device-key slot can still read them. Each SK is named by its 16-byte
 * id; its records are never removed. [CURRENT_KEY] names the SK in use.
 *
 * - The PIN wrap: SK under KEK = HKDF-SHA256(Argon2id(PIN, salt) ‖
 *   HMAC-SHA256(D, salt)), where D is the [WalletKeyringDeviceFactor] kept
 *   under the device key. Opening it is the PIN check. A PIN change replaces
 *   only this record; SK and every sealed value stay as they are.
 * - Backup wraps, one per wallet root: SK under KEK = HKDF-SHA256 of the
 *   root's canonical private key ([WalletRootKeyKind.canonical]). Proving any
 *   one backup opens SK, as PIN recovery by backup works today.
 * - The sealing mark: stored before the first value is sealed under SK. While
 *   it is absent, nothing was ever sealed under SK, even if some stored values
 *   cannot be read right now.
 *
 * Guessing the PIN needs D, which a copy of the app's files without the device
 * key does not have. Code running as the app can unwrap the device key and
 * guess offline at the Argon2id cost per guess, so a longer PIN is stronger.
 *
 * There is no biometric wrap: the biometric vault unseals the PIN, and the PIN
 * wrap gives SK.
 */
class WalletSecretKeyring(
    private val preferences: Preferences,
    private val deviceFactor: WalletKeyringDeviceFactor,
    private val random: SecureRandom = SecureRandom(),
    private val newWrapKdf: WalletPinKdfParameters = WalletPinKdfParameters.DEFAULT
) {

    /** SK and its id for one operation. Zero it with [close] when done. */
    class OpenedKey(keyId: ByteArray, key: ByteArray) {
        val keyId: ByteArray = keyId.copyOf()
        val key: ByteArray = key.copyOf()

        fun close() {
            key.fill(0)
        }

        override fun toString(): String = "WalletSecretKeyring.OpenedKey(<redacted>)"
    }

    enum class PinWrapState {
        /** No keyring: the PIN is still the legacy `pin_code` preference, or none was set. */
        ABSENT,

        /** A well-formed PIN wrap whose device factor still authenticates. */
        USABLE,

        /** A keyring whose PIN wrap can never open: malformed, or its device factor is gone or corrupt. */
        UNUSABLE
    }

    sealed interface PinOpening {
        class Opened(val key: OpenedKey) : PinOpening
        object WrongPin : PinOpening
        object Unusable : PinOpening
    }

    fun exists(): Boolean = preferences.contains(CURRENT_KEY) || preferences.hasKeyWithPrefix(RECORD_PREFIX)

    fun currentKeyId(): ByteArray? = preferences.getString(CURRENT_KEY)?.let(::decodeKeyIdOrNull)

    /** The exact stored PIN wrap, for PIN-reset snapshots; it is not secret on its own. */
    fun pinRecord(): String? = currentKeyId()?.let { preferences.getString(pinKey(it)) }

    /** An unavailable device key propagates: that is "try again", never an unusable wrap. */
    fun pinWrapState(): PinWrapState {
        if (!exists()) return PinWrapState.ABSENT
        val record = pinRecord()?.let(::parsePinWrapOrNull) ?: return PinWrapState.UNUSABLE
        return if (deviceFactorUsable(record.alias)) PinWrapState.USABLE else PinWrapState.UNUSABLE
    }

    /** Opening the PIN wrap is the PIN check. Costs one Argon2id run. */
    fun openWithPin(pin: String): PinOpening {
        val keyId = currentKeyId() ?: return PinOpening.Unusable
        val record = preferences.getString(pinKey(keyId))?.let(::parsePinWrapOrNull)
            ?: return PinOpening.Unusable
        if (!record.keyIdMatches(keyId)) return PinOpening.Unusable
        val kek = try {
            pinKek(pin, record)
        } catch (_: WalletKeyringDeviceFactorLostException) {
            return PinOpening.Unusable
        }
        return try {
            val key = record.open(kek)
            try {
                PinOpening.Opened(OpenedKey(keyId, key))
            } finally {
                key.fill(0)
            }
        } catch (_: BadPaddingException) {
            // AEADBadTagException: the PIN-derived KEK did not authenticate the wrap.
            PinOpening.WrongPin
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Creates SK and its PIN wrap for a keyring that does not exist yet, then
     * reads the stored wrap back and opens it through the device factor again. A wrap
     * that does not open is removed before anything was sealed with it.
     */
    @Synchronized
    fun create(pin: String): OpenedKey {
        check(!exists()) { "A wallet keyring already exists" }
        return newGeneration(pin, previousCurrent = null)
    }

    /**
     * Starts a new SK for a keyring whose PIN wrap can never open again, under
     * [pin], which the caller has just proved another way (the legacy PIN).
     * Only for a keyring that holds nothing yet: the caller has checked that
     * no stored value is sealed. The old SK's records stay where they are.
     */
    @Synchronized
    fun replaceUnusable(pin: String, expectedKeyId: ByteArray): OpenedKey {
        val current = checkNotNull(currentKeyId()) { "There is no wallet keyring" }
        check(MessageDigest.isEqual(current, expectedKeyId)) { "The wallet keyring changed meanwhile" }
        check(pinWrapState() == PinWrapState.UNUSABLE) { "The PIN wrap still opens; it is never replaced" }
        return newGeneration(pin, previousCurrent = preferences.getString(CURRENT_KEY))
    }

    private fun newGeneration(pin: String, previousCurrent: String?): OpenedKey {
        val keyId = ByteArray(KEY_ID_BYTES).also(random::nextBytes)
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        try {
            val alias = deviceFactor.create()
            val values = try {
                mapOf(CURRENT_KEY to Hex.toHexString(keyId), pinKey(keyId) to newPinWrap(keyId, key, pin, alias))
            } catch (failure: Exception) {
                removeUnreferencedFactor(alias)
                throw failure
            }
            try {
                check(preferences.replaceStringsDurably(values, emptySet())) { "The keyring was not stored durably" }
                check(values.all { (name, value) -> preferences.getString(name) == value }) { "The keyring read-back differs" }
                verifyPinWrap(keyId, key, pin)
            } catch (failure: Exception) {
                // Nothing was sealed with this SK yet; take back exactly what was written.
                rollBack(values, previousCurrent)
                removeUnreferencedFactor(alias)
                throw failure
            }
            return OpenedKey(keyId, key)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Rewraps the open SK with [newPin]. Only the PIN wrap changes; sealed
     * values are untouched. [expectedRecord] binds the change to the wrap the
     * caller authorized against. A device factor that is gone or corrupt is replaced.
     */
    @Synchronized
    fun replacePin(opened: OpenedKey, newPin: String, expectedRecord: String?) {
        val keyId = checkNotNull(currentKeyId()) { "There is no wallet keyring" }
        check(MessageDigest.isEqual(keyId, opened.keyId)) { "The open SK is not this keyring's SK" }
        val name = pinKey(keyId)
        val previous = preferences.getString(name)
        if (expectedRecord != null && previous != expectedRecord) {
            throw IllegalStateException("The PIN changed while its replacement was prepared")
        }
        val previousAlias = previous?.let(::parsePinWrapOrNull)?.alias
        val reused = previousAlias?.takeIf(::deviceFactorUsable)
        val alias = reused ?: deviceFactor.create()
        val record = try {
            newPinWrap(keyId, opened.key, newPin, alias)
        } catch (failure: Exception) {
            if (reused == null) removeUnreferencedFactor(alias)
            throw failure
        }
        try {
            check(preferences.replaceStringsDurably(mapOf(name to record), emptySet())) { "The PIN wrap was not stored durably" }
            check(preferences.getString(name) == record) { "The PIN wrap read-back differs" }
            verifyPinWrap(keyId, opened.key, newPin)
        } catch (failure: Exception) {
            if (preferences.getString(name) == record) {
                if (previous != null) {
                    preferences.replaceStringsDurably(mapOf(name to previous), emptySet())
                } else {
                    preferences.replaceStringsDurably(emptyMap(), setOf(name))
                }
            }
            if (reused == null && preferences.getString(name) != record) removeUnreferencedFactor(alias)
            throw failure
        }
    }

    /**
     * Stores the sealing mark of the open SK, durably, before its first value
     * is sealed. Throws when the mark cannot be stored; nothing may be sealed
     * then. The mark is never removed.
     */
    @Synchronized
    fun beginSealing(opened: OpenedKey) {
        val keyId = checkNotNull(currentKeyId()) { "There is no wallet keyring" }
        check(MessageDigest.isEqual(keyId, opened.keyId)) { "The open SK is not this keyring's SK" }
        val name = sealingKey(keyId)
        val stored = try {
            preferences.getString(name)
        } catch (_: ClassCastException) {
            null
        }
        if (stored == SEALING_BEGUN) return
        check(preferences.replaceStringsDurably(mapOf(name to SEALING_BEGUN), emptySet())) { "The sealing mark was not stored durably" }
        check(preferences.getString(name) == SEALING_BEGUN) { "The sealing mark read-back differs" }
    }

    /**
     * Whether a value may have been sealed under the current SK: its sealing
     * mark is stored, or the current SK cannot be named, so nothing can be ruled out.
     */
    fun mayHoldSealedValues(): Boolean {
        val keyId = currentKeyId() ?: return true
        return preferences.contains(sealingKey(keyId))
    }

    fun hasBackupWrap(keyId: ByteArray, canonicalRoot: ByteArray): Boolean =
        preferences.contains(backupKey(keyId, rootId(canonicalRoot)))

    /**
     * Adds the backup wrap of one root. A wrap that already opens with this
     * root is kept as it is; a stored record that does not authenticate under
     * this root's key holds nothing usable and is replaced.
     */
    @Synchronized
    fun addBackupWrap(opened: OpenedKey, canonicalRoot: ByteArray) {
        val keyId = checkNotNull(currentKeyId()) { "There is no wallet keyring" }
        check(MessageDigest.isEqual(keyId, opened.keyId)) { "The open SK is not this keyring's SK" }
        val rootId = rootId(canonicalRoot)
        val name = backupKey(keyId, rootId)
        val kek = backupKek(canonicalRoot, keyId, rootId)
        try {
            val existing = preferences.getString(name)
            if (existing != null && opensWith(existing, kek, opened.key)) return
            val record = BackupWrap.seal(keyId, rootId, opened.key, kek, random)
            check(preferences.getString(name) == existing) { "A backup wrap changed while it was replaced" }
            check(preferences.replaceStringsDurably(mapOf(name to record), emptySet())) { "A backup wrap was not stored durably" }
            check(preferences.getString(name) == record && opensWith(record, kek, opened.key)) { "A backup wrap does not open" }
        } finally {
            kek.fill(0)
        }
    }

    private fun opensWith(record: String, kek: ByteArray, expectedKey: ByteArray): Boolean {
        val wrap = parseBackupWrapOrNull(record) ?: return false
        val key = try {
            wrap.open(kek)
        } catch (_: BadPaddingException) {
            return false
        }
        return try {
            MessageDigest.isEqual(key, expectedKey)
        } finally {
            key.fill(0)
        }
    }

    /** Opens SK with any proved root, trying each candidate form of its key. */
    fun openWithBackup(canonicalCandidates: Collection<ByteArray>): OpenedKey? {
        val keyId = currentKeyId() ?: return null
        for (candidate in canonicalCandidates) {
            val rootId = rootId(candidate)
            val record = preferences.getString(backupKey(keyId, rootId))?.let(::parseBackupWrapOrNull) ?: continue
            if (!record.keyIdMatches(keyId) || !record.rootIdMatches(rootId)) continue
            val kek = backupKek(candidate, keyId, rootId)
            try {
                val key = record.open(kek)
                try {
                    return OpenedKey(keyId, key)
                } finally {
                    key.fill(0)
                }
            } catch (_: BadPaddingException) {
                continue
            } finally {
                kek.fill(0)
            }
        }
        return null
    }

    /** Whether the factor is stored and still authenticates; an unavailable device key propagates. */
    private fun deviceFactorUsable(alias: String): Boolean {
        if (!deviceFactor.exists(alias)) return false
        return try {
            deviceFactor.hmac(alias, FACTOR_PROBE).fill(0)
            true
        } catch (_: WalletKeyringDeviceFactorLostException) {
            false
        }
    }

    /** Removes a factor this call created that no stored PIN wrap names. */
    private fun removeUnreferencedFactor(alias: String) {
        if (!alias.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX) || !preferences.contains(alias)) return
        val referenced = currentKeyId()?.let { preferences.getString(pinKey(it)) }?.let(::parsePinWrapOrNull)?.alias == alias
        if (!referenced) preferences.replaceStringsDurably(emptyMap(), setOf(alias))
    }

    private fun verifyPinWrap(keyId: ByteArray, key: ByteArray, pin: String) {
        when (val reopened = openWithPin(pin)) {
            is PinOpening.Opened -> try {
                check(MessageDigest.isEqual(reopened.key.keyId, keyId) && MessageDigest.isEqual(reopened.key.key, key)) {
                    "The stored PIN wrap opens another key"
                }
            } finally {
                reopened.key.close()
            }

            else -> error("The stored PIN wrap does not open with its PIN")
        }
    }

    /** Takes back what a failed generation wrote; a replaced keyring gets its previous current id back. */
    private fun rollBack(values: Map<String, String>, previousCurrent: String?) {
        val written = values.filter { (name, value) -> preferences.getString(name) == value }.keys
        if (written.isEmpty()) return
        if (previousCurrent != null && CURRENT_KEY in written) {
            preferences.replaceStringsDurably(mapOf(CURRENT_KEY to previousCurrent), written - CURRENT_KEY)
        } else {
            preferences.replaceStringsDurably(emptyMap(), written)
        }
    }

    private fun newPinWrap(keyId: ByteArray, key: ByteArray, pin: String, alias: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = PinWrapHeader(keyId, newWrapKdf, salt, alias)
        val kek = pinKek(pin, header)
        try {
            return header.seal(key, kek, random)
        } finally {
            kek.fill(0)
        }
    }

    private fun pinKek(pin: String, header: PinWrapHeader): ByteArray {
        // The factor first: a lost or unreadable factor costs no Argon2id run.
        val bound = deviceFactor.hmac(header.alias, header.salt)
        val stretched = try {
            WalletPinKdf.derive(pin, header.salt, header.kdf)
        } catch (failure: Exception) {
            bound.fill(0)
            throw failure
        }
        val ikm = stretched + bound
        try {
            return hkdf(ikm, PIN_WRAP_SALT, header.keyIdCopy())
        } finally {
            stretched.fill(0)
            bound.fill(0)
            ikm.fill(0)
        }
    }

    private fun backupKek(canonicalRoot: ByteArray, keyId: ByteArray, rootId: ByteArray): ByteArray =
        hkdf(canonicalRoot, BACKUP_WRAP_SALT, keyId + rootId)

    private fun parsePinWrapOrNull(record: String): PinWrap? = try {
        PinWrap.parse(record)
    } catch (_: Exception) {
        null
    }

    private fun parseBackupWrapOrNull(record: String): BackupWrap? = try {
        BackupWrap.parse(record)
    } catch (_: Exception) {
        null
    }

    private fun decodeKeyIdOrNull(hex: String): ByteArray? = try {
        Hex.decode(hex).takeIf { it.size == KEY_ID_BYTES && Hex.toHexString(it) == hex }
    } catch (_: Exception) {
        null
    }

    private open class PinWrapHeader(keyId: ByteArray, val kdf: WalletPinKdfParameters, salt: ByteArray, val alias: String) {
        private val keyIdBytes = keyId.copyOf()
        val salt: ByteArray = salt.copyOf()

        init {
            require(keyIdBytes.size == KEY_ID_BYTES && this.salt.size == SALT_BYTES)
            require(alias.isNotEmpty() && alias.length <= MAX_ALIAS_CHARS && alias.all { it in ' '..'~' })
            require(kdf.isAcceptable)
        }

        fun keyIdCopy(): ByteArray = keyIdBytes.copyOf()

        fun keyIdMatches(other: ByteArray) = MessageDigest.isEqual(keyIdBytes, other)

        fun encodeHeader(): ByteArray = ByteArrayOutputStream().apply {
            write(PIN_WRAP_MAGIC)
            write(RECORD_VERSION)
            write(keyIdBytes)
            write(kdf.algorithm)
            writeInt(kdf.memoryKiB)
            writeInt(kdf.iterations)
            write(kdf.parallelism)
            write(salt)
            write(alias.length)
            write(alias.toByteArray(Charsets.US_ASCII))
        }.toByteArray()

        fun seal(key: ByteArray, kek: ByteArray, random: SecureRandom): String {
            val header = encodeHeader()
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val sealed = gcm(Cipher.ENCRYPT_MODE, kek, nonce, header).doFinal(key)
            return encoder.encodeToString(header + nonce + sealed)
        }
    }

    private class PinWrap(
        keyId: ByteArray,
        kdf: WalletPinKdfParameters,
        salt: ByteArray,
        alias: String,
        private val nonce: ByteArray,
        private val sealed: ByteArray
    ) : PinWrapHeader(keyId, kdf, salt, alias) {

        fun open(kek: ByteArray): ByteArray =
            gcm(Cipher.DECRYPT_MODE, kek, nonce, encodeHeader()).doFinal(sealed).also {
                check(it.size == KEY_BYTES) { "A PIN wrap holds a malformed SK" }
            }

        companion object {
            fun parse(record: String): PinWrap {
                val reader = RecordReader(decodeRecord(record))
                check(reader.bytes(PIN_WRAP_MAGIC.size).contentEquals(PIN_WRAP_MAGIC)) { "Not a PIN wrap" }
                check(reader.byte() == RECORD_VERSION) { "Unsupported PIN wrap version" }
                val keyId = reader.bytes(KEY_ID_BYTES)
                val kdf = WalletPinKdfParameters(reader.byte(), reader.int(), reader.int(), reader.byte())
                val salt = reader.bytes(SALT_BYTES)
                val alias = String(reader.bytes(reader.byte()), Charsets.US_ASCII)
                val nonce = reader.bytes(NONCE_BYTES)
                val sealed = reader.rest()
                check(sealed.size == KEY_BYTES + TAG_BYTES) { "A PIN wrap is truncated" }
                return PinWrap(keyId, kdf, salt, alias, nonce, sealed)
            }
        }
    }

    private class BackupWrap(
        keyId: ByteArray,
        rootId: ByteArray,
        private val nonce: ByteArray,
        private val sealed: ByteArray
    ) {
        private val keyIdBytes = keyId.copyOf()
        private val rootIdBytes = rootId.copyOf()

        fun keyIdMatches(other: ByteArray) = MessageDigest.isEqual(keyIdBytes, other)
        fun rootIdMatches(other: ByteArray) = MessageDigest.isEqual(rootIdBytes, other)

        fun open(kek: ByteArray): ByteArray =
            gcm(Cipher.DECRYPT_MODE, kek, nonce, header(keyIdBytes, rootIdBytes)).doFinal(sealed).also {
                check(it.size == KEY_BYTES) { "A backup wrap holds a malformed SK" }
            }

        companion object {
            fun header(keyId: ByteArray, rootId: ByteArray): ByteArray =
                BACKUP_WRAP_MAGIC + byteArrayOf(RECORD_VERSION.toByte()) + keyId + rootId

            fun seal(keyId: ByteArray, rootId: ByteArray, key: ByteArray, kek: ByteArray, random: SecureRandom): String {
                val header = header(keyId, rootId)
                val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
                val sealed = gcm(Cipher.ENCRYPT_MODE, kek, nonce, header).doFinal(key)
                return encoder.encodeToString(header + nonce + sealed)
            }

            fun parse(record: String): BackupWrap {
                val reader = RecordReader(decodeRecord(record))
                check(reader.bytes(BACKUP_WRAP_MAGIC.size).contentEquals(BACKUP_WRAP_MAGIC)) { "Not a backup wrap" }
                check(reader.byte() == RECORD_VERSION) { "Unsupported backup wrap version" }
                val keyId = reader.bytes(KEY_ID_BYTES)
                val rootId = reader.bytes(ROOT_ID_BYTES)
                val nonce = reader.bytes(NONCE_BYTES)
                val sealed = reader.rest()
                check(sealed.size == KEY_BYTES + TAG_BYTES) { "A backup wrap is truncated" }
                return BackupWrap(keyId, rootId, nonce, sealed)
            }
        }
    }

    private class RecordReader(private val bytes: ByteArray) {
        private var position = 0

        fun byte(): Int {
            need(1)
            return bytes[position++].toInt() and 0xFF
        }

        fun int(): Int = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()

        fun bytes(count: Int): ByteArray {
            need(count)
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        fun rest(): ByteArray = bytes.copyOfRange(position, bytes.size).also { position = bytes.size }

        private fun need(count: Int) = check(count >= 0 && bytes.size - position >= count) { "A keyring record is truncated" }
    }

    companion object {
        const val KEY_PREFIX = "wallet_secret_keyring_v1:"
        private const val CURRENT_KEY = "${KEY_PREFIX}current"
        private const val RECORD_PREFIX = "${KEY_PREFIX}sk:"
        private const val SEALING_BEGUN = "1"

        private const val KEY_ID_BYTES = WalletSecretEnvelope.KEY_ID_BYTES
        private const val KEY_BYTES = WalletSecretEnvelope.KEY_BYTES
        private const val ROOT_ID_BYTES = 16
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val MAX_ALIAS_CHARS = 128
        private const val MAX_RECORD_CHARS = 1_024
        private const val RECORD_VERSION = 1
        private val PIN_WRAP_MAGIC = "FPW1".toByteArray(Charsets.US_ASCII)
        private val BACKUP_WRAP_MAGIC = "FBW1".toByteArray(Charsets.US_ASCII)
        private val PIN_WRAP_SALT = "fearless-wallet-sk-pin-wrap:v1".toByteArray(Charsets.US_ASCII)
        private val BACKUP_WRAP_SALT = "fearless-wallet-sk-backup-wrap:v1".toByteArray(Charsets.US_ASCII)
        private val ROOT_ID_DOMAIN = "fearless-wallet-sk-root:v1".toByteArray(Charsets.US_ASCII)
        private val FACTOR_PROBE = "fearless-wallet-sk-factor-probe:v1".toByteArray(Charsets.US_ASCII)

        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        fun pinKey(keyId: ByteArray): String = "$RECORD_PREFIX${Hex.toHexString(keyId)}:pin"

        fun backupKey(keyId: ByteArray, rootId: ByteArray): String =
            "$RECORD_PREFIX${Hex.toHexString(keyId)}:backup:${Hex.toHexString(rootId)}"

        /** Where the sealing mark of SK [keyId] is stored. */
        fun sealingKey(keyId: ByteArray): String = "$RECORD_PREFIX${Hex.toHexString(keyId)}:sealing"

        /** A root's id: a digest of its canonical key, so every proof of that root finds its wrap. */
        fun rootId(canonicalRoot: ByteArray): ByteArray {
            require(canonicalRoot.size == KEY_BYTES) { "A canonical root key has 32 bytes" }
            return MessageDigest.getInstance("SHA-256").run {
                update(ROOT_ID_DOMAIN)
                update(canonicalRoot)
                digest()
            }.copyOf(ROOT_ID_BYTES)
        }

        private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
            val generator = HKDFBytesGenerator(SHA256Digest())
            generator.init(HKDFParameters(ikm, salt, info))
            return ByteArray(KEY_BYTES).also { generator.generateBytes(it, 0, it.size) }
        }

        private fun gcm(mode: Int, kek: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(aad)
            }

        private fun decodeRecord(record: String): ByteArray {
            check(record.length <= MAX_RECORD_CHARS && record.none { it == '=' || it == '+' || it == '/' }) {
                "A keyring record is malformed"
            }
            return decoder.decode(record)
        }

        private fun ByteArrayOutputStream.writeInt(value: Int) {
            write(value ushr 24)
            write(value ushr 16)
            write(value ushr 8)
            write(value)
        }
    }
}
