package jp.co.soramitsu.common.data.secrets

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bouncycastle.util.encoders.Hex

/**
 * Emergency key access (addendum U0.4): everything this device stores for
 * any wallet, read straight from encrypted preferences, exactly as stored and
 * without validating it. Active keys, keys set aside for recovery, archived
 * keys and pre-4.0 formats are all listed, so a user in any recovery or
 * start-up failure state can write down what the device holds.
 *
 * It reads key names and values only; it never writes, moves or deletes
 * anything, and never needs the wallet database. Callers must authenticate
 * the user first and must not log or persist what it returns.
 */
@Singleton
class WalletEmergencyRecoveryReader @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences
) {

    fun read(): List<EmergencyRecoveryEntry> {
        return storedKeys()
            .sortedWith(STORED_KEY_ORDER)
            .map(::readEntry)
    }

    /** How many wallet keys are stored, from key names only; nothing is decrypted. */
    fun storedKeyCount(): Int = storedKeys().size

    /**
     * Names only, from the current preference snapshot. Retains at most one page
     * and one lookahead name; never decrypts values or counts the whole inventory.
     * A missing cursor fails explicitly so the caller can offer a fresh first page.
     */
    suspend fun listPage(afterStorageKey: String? = null, pageSize: Int = MAX_PAGE_SIZE): EmergencyRecoveryPage {
        require(pageSize in 1..MAX_PAGE_SIZE)
        val context = currentCoroutineContext()
        context.ensureActive()
        val after = afterStorageKey?.let(::requireStoredKey)
        var cursorFound = after == null
        val selected = PriorityQueue(pageSize + 1, STORED_KEY_ORDER.reversed())
        encryptedPreferences.forEachKeyWithPrefixes(SCANNED_PREFIXES, MAX_KEY_BYTES) { storageKey ->
            context.ensureActive()
            val key = locate(storageKey) ?: return@forEachKeyWithPrefixes
            if (storageKey == afterStorageKey) cursorFound = true
            if (after != null && STORED_KEY_ORDER.compare(key, after) <= 0) return@forEachKeyWithPrefixes
            if (selected.size < pageSize + 1) {
                selected.add(key)
            } else if (STORED_KEY_ORDER.compare(key, selected.peek()) < 0) {
                selected.remove()
                selected.add(key)
            }
        }
        if (!cursorFound) throw EmergencyRecoveryPageChangedException()
        val sorted = selected.sortedWith(STORED_KEY_ORDER)
        val visible = sorted.take(pageSize)
        return EmergencyRecoveryPage(
            keys = visible.map { EmergencyRecoveryKeyReference(it.storageKey, it.walletId, it.state, it.kind) },
            nextStorageKey = visible.lastOrNull()?.storageKey?.takeIf { sorted.size > pageSize }
        )
    }

    /** Names-only presence probe for the unavailable-PIN response; no values are decrypted. */
    suspend fun hasStoredKeys(): Boolean {
        val context = currentCoroutineContext()
        context.ensureActive()
        var found = false
        encryptedPreferences.forEachKeyWithPrefixes(SCANNED_PREFIXES, MAX_KEY_BYTES) { storageKey ->
            context.ensureActive()
            if (!found && locate(storageKey) != null) found = true
        }
        return found
    }

    /** Call only after authentication and an explicit selection; re-read the selected value now. */
    suspend fun readKey(storageKey: String): EmergencyRecoveryEntry {
        val context = currentCoroutineContext()
        context.ensureActive()
        val entry = readEntry(requireStoredKey(storageKey))
        context.ensureActive()
        return entry
    }

    private fun requireStoredKey(storageKey: String): StoredKey {
        require(storageKey.length <= MAX_KEY_BYTES && storageKey.encodeToByteArray().size <= MAX_KEY_BYTES)
        return requireNotNull(locate(storageKey)) { "Not a recognized emergency recovery key" }
    }

    private fun storedKeys(): List<StoredKey> {
        val result = mutableListOf<StoredKey>()
        var totalKeyBytes = 0
        encryptedPreferences.forEachKeyWithPrefixes(SCANNED_PREFIXES, MAX_KEY_BYTES) { storageKey ->
            val storedKey = locate(storageKey) ?: return@forEachKeyWithPrefixes
            val keyBytes = storageKey.encodeToByteArray().size
            check(result.size < MAX_KEYS) {
                "The emergency recovery key inventory matched too many wallet keys"
            }
            check(totalKeyBytes <= MAX_TOTAL_KEY_BYTES - keyBytes) {
                "The emergency recovery key inventory matched too many wallet key bytes"
            }
            result += storedKey
            totalKeyBytes += keyBytes
        }
        return result
    }

    private fun readEntry(key: StoredKey): EmergencyRecoveryEntry {
        // A provider outage stops the whole read; one undecodable value never does.
        val plaintext = readOrNull(rethrow = { it is WalletSecureStorageUnavailableException }) {
            encryptedPreferences.getDecryptedString(key.storageKey)
        }
        if (plaintext.isNullOrEmpty()) {
            return key.toEntry(readable = false, fields = emptyList())
        }
        val fields = readOrNull(rethrow = { false }) { decode(key.kind, plaintext) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(EmergencyRecoveryField(EmergencyRecoveryFieldType.STORED_DATA, plaintext))
        return key.toEntry(readable = true, fields = fields)
    }

    private fun decode(
        kind: EmergencyRecoveryKeyKind,
        plaintext: String
    ): List<EmergencyRecoveryField> = when (kind) {
        EmergencyRecoveryKeyKind.SUBSTRATE -> {
            val secrets = SubstrateSecrets.read(scaleHex(plaintext))
            buildFields {
                phrase(secrets[SubstrateSecrets.Entropy])
                hex(EmergencyRecoveryFieldType.RAW_SEED, secrets[SubstrateSecrets.Seed])
                text(EmergencyRecoveryFieldType.DERIVATION_PATH, secrets[SubstrateSecrets.SubstrateDerivationPath])
                keypair(EmergencyRecoveryFieldType.PRIVATE_KEY, secrets[SubstrateSecrets.SubstrateKeypair])
            }
        }

        EmergencyRecoveryKeyKind.ETHEREUM -> {
            val secrets = EthereumSecrets.read(scaleHex(plaintext))
            buildFields {
                phrase(secrets[EthereumSecrets.Entropy])
                hex(EmergencyRecoveryFieldType.RAW_SEED, secrets[EthereumSecrets.Seed])
                text(
                    EmergencyRecoveryFieldType.ETHEREUM_DERIVATION_PATH,
                    secrets[EthereumSecrets.EthereumDerivationPath]
                )
                keypair(EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY, secrets[EthereumSecrets.EthereumKeypair])
            }
        }

        EmergencyRecoveryKeyKind.TON -> {
            val secrets = TonSecrets.read(scaleHex(plaintext))
            buildFields {
                // 4.x derived the TON key from the text exactly as typed;
                // spaces and capitals are kept.
                val phrase = secrets[TonSecrets.Seed]
                val text = phrase.strictUtf8()
                if (text != null) {
                    text(EmergencyRecoveryFieldType.TON_PHRASE, text)
                } else {
                    hex(EmergencyRecoveryFieldType.STORED_DATA, phrase)
                }
                hex(EmergencyRecoveryFieldType.TON_PRIVATE_KEY, secrets[TonSecrets.PrivateKey])
            }
        }

        EmergencyRecoveryKeyKind.LEGACY_WALLET -> decodeLegacyWallet(scaleHex(plaintext))

        EmergencyRecoveryKeyKind.NETWORK_ACCOUNT -> {
            val secrets = ChainAccountSecrets.read(scaleHex(plaintext))
            buildFields {
                phrase(secrets[ChainAccountSecrets.Entropy])
                hex(EmergencyRecoveryFieldType.RAW_SEED, secrets[ChainAccountSecrets.Seed])
                text(EmergencyRecoveryFieldType.DERIVATION_PATH, secrets[ChainAccountSecrets.DerivationPath])
                keypair(EmergencyRecoveryFieldType.PRIVATE_KEY, secrets[ChainAccountSecrets.Keypair])
            }
        }

        EmergencyRecoveryKeyKind.LEGACY_V1 -> {
            val source = SourceInternal.read(scaleHex(plaintext))
            buildFields {
                text(EmergencyRecoveryFieldType.RECOVERY_PHRASE, source[SourceInternal.Mnemonic])
                hex(EmergencyRecoveryFieldType.RAW_SEED, source[SourceInternal.Seed])
                text(EmergencyRecoveryFieldType.DERIVATION_PATH, source[SourceInternal.DerivationPath])
                hex(EmergencyRecoveryFieldType.PRIVATE_KEY, source[SourceInternal.PrivateKey])
                hex(EmergencyRecoveryFieldType.NONCE, source[SourceInternal.Nonce])
            }
        }

        EmergencyRecoveryKeyKind.LEGACY_V04 -> {
            val signing = LegacyWalletV04Secrets.decodeSigningData(plaintext)
            buildFields {
                hex(EmergencyRecoveryFieldType.PRIVATE_KEY, signing.privateKey)
                hex(EmergencyRecoveryFieldType.NONCE, signing.nonce)
            }
        }

        EmergencyRecoveryKeyKind.LEGACY_V04_TEXT -> buildFields {
            text(EmergencyRecoveryFieldType.STORED_DATA, plaintext)
        }
    }

    /** The current seven-field V2 payload, or the six-field one written before TON. */
    @Suppress("DEPRECATION")
    private fun decodeLegacyWallet(encoded: String): List<EmergencyRecoveryField> {
        return when (WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(encoded)) {
            WalletMetaAccountScaleLayout.CURRENT_V2 -> {
                val secrets = MetaAccountSecrets.read(encoded)
                buildFields {
                    phrase(secrets[MetaAccountSecrets.Entropy])
                    hex(EmergencyRecoveryFieldType.RAW_SEED, secrets[MetaAccountSecrets.Seed])
                    text(EmergencyRecoveryFieldType.DERIVATION_PATH, secrets[MetaAccountSecrets.SubstrateDerivationPath])
                    keypair(EmergencyRecoveryFieldType.PRIVATE_KEY, secrets[MetaAccountSecrets.SubstrateKeypair])
                    secrets[MetaAccountSecrets.EthereumKeypair]?.let {
                        keypair(EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY, it)
                    }
                    text(
                        EmergencyRecoveryFieldType.ETHEREUM_DERIVATION_PATH,
                        secrets[MetaAccountSecrets.EthereumDerivationPath]
                    )
                    secrets[MetaAccountSecrets.TonKeypair]?.let {
                        keypair(EmergencyRecoveryFieldType.TON_PRIVATE_KEY, it)
                    }
                }
            }

            WalletMetaAccountScaleLayout.LEGACY_V69 -> {
                val secrets = MetaAccountSecretsV69.read(encoded)
                buildFields {
                    phrase(secrets[MetaAccountSecretsV69.Entropy])
                    hex(EmergencyRecoveryFieldType.RAW_SEED, secrets[MetaAccountSecretsV69.Seed])
                    text(
                        EmergencyRecoveryFieldType.DERIVATION_PATH,
                        secrets[MetaAccountSecretsV69.SubstrateDerivationPath]
                    )
                    keypair(EmergencyRecoveryFieldType.PRIVATE_KEY, secrets[MetaAccountSecretsV69.SubstrateKeypair])
                    secrets[MetaAccountSecretsV69.EthereumKeypair]?.let {
                        keypair(EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY, it)
                    }
                    text(
                        EmergencyRecoveryFieldType.ETHEREUM_DERIVATION_PATH,
                        secrets[MetaAccountSecretsV69.EthereumDerivationPath]
                    )
                }
            }
        }
    }

    private fun scaleHex(plaintext: String): String =
        WalletSecretScalePreflight.normalizePersistedHex(plaintext)

    private fun locate(storageKey: String): StoredKey? {
        val (state, named) = when {
            storageKey.startsWith(WalletSecretQuarantine.KEY_PREFIX) ->
                EmergencyRecoveryState.SET_ASIDE to storageKey.removePrefix(WalletSecretQuarantine.KEY_PREFIX)

            storageKey.startsWith(WalletSecretArchive.KEY_PREFIX) ->
                EmergencyRecoveryState.ARCHIVED to (WalletSecretArchive.sourceKeyOf(storageKey) ?: return null)

            else -> EmergencyRecoveryState.ACTIVE to storageKey
        }
        val (walletId, kind) = walletKey(named) ?: earlyFormatKey(named) ?: return null
        return StoredKey(storageKey, walletId, state, kind)
    }

    /** `<id>:<root>_SECRETS` and `<id>:<accountIdHex>:ACCESS_SECRETS`. */
    private fun walletKey(named: String): Pair<Long?, EmergencyRecoveryKeyKind>? {
        rootKey.matchEntire(named)?.let { match ->
            val kind = when (match.groupValues[2]) {
                "ACCESS_SECRETS" -> EmergencyRecoveryKeyKind.LEGACY_WALLET
                "SUBSTRATE_SECRETS" -> EmergencyRecoveryKeyKind.SUBSTRATE
                "ETHEREUM_SECRETS" -> EmergencyRecoveryKeyKind.ETHEREUM
                else -> EmergencyRecoveryKeyKind.TON
            }
            return match.groupValues[1].toLongOrNull() to kind
        }
        return chainAccountKey.matchEntire(named)?.let { match ->
            match.groupValues[1].toLongOrNull() to EmergencyRecoveryKeyKind.NETWORK_ACCOUNT
        }
    }

    /** 1.x `security_source_<address>`, 0.4 keys, and their quarantine names. */
    private fun earlyFormatKey(named: String): Pair<Long?, EmergencyRecoveryKeyKind>? {
        legacyQuarantine.matchEntire(named)?.let { match ->
            val kind = if (match.groupValues[1] == "04") {
                EmergencyRecoveryKeyKind.LEGACY_V04
            } else {
                EmergencyRecoveryKeyKind.LEGACY_V1
            }
            return match.groupValues[2].toLongOrNull() to kind
        }
        return when {
            named.startsWith(LEGACY_V1_PREFIX) && named.length > LEGACY_V1_PREFIX.length ->
                null to EmergencyRecoveryKeyKind.LEGACY_V1

            LegacyWalletV04Secrets.isSigningDataKey(named) -> null to EmergencyRecoveryKeyKind.LEGACY_V04
            LegacyWalletV04Secrets.isLegacyKey(named) -> null to EmergencyRecoveryKeyKind.LEGACY_V04_TEXT
            else -> null
        }
    }

    private class StoredKey(
        val storageKey: String,
        val walletId: Long?,
        val state: EmergencyRecoveryState,
        val kind: EmergencyRecoveryKeyKind
    ) {
        fun toEntry(readable: Boolean, fields: List<EmergencyRecoveryField>) = EmergencyRecoveryEntry(
            storageKey = storageKey,
            walletId = walletId,
            state = state,
            kind = kind,
            readable = readable,
            fields = fields
        )
    }

    private class FieldsBuilder {
        val fields = mutableListOf<EmergencyRecoveryField>()

        fun phrase(entropy: ByteArray?) {
            entropy ?: return
            val words = try {
                MnemonicCreator.fromEntropy(entropy.clone()).words
            } catch (_: Exception) {
                null
            }
            if (words != null) {
                text(EmergencyRecoveryFieldType.RECOVERY_PHRASE, words)
            } else {
                hex(EmergencyRecoveryFieldType.ENTROPY, entropy)
            }
        }

        fun text(type: EmergencyRecoveryFieldType, value: String?) {
            if (!value.isNullOrEmpty()) fields += EmergencyRecoveryField(type, value)
        }

        fun hex(type: EmergencyRecoveryFieldType, value: ByteArray?) {
            if (value != null && value.isNotEmpty()) {
                fields += EmergencyRecoveryField(type, "0x" + Hex.toHexString(value))
            }
        }

        fun keypair(type: EmergencyRecoveryFieldType, keypair: EncodableStruct<KeyPairSchema>) {
            hex(type, keypair[KeyPairSchema.PrivateKey])
            hex(EmergencyRecoveryFieldType.NONCE, keypair[KeyPairSchema.Nonce])
        }
    }

    private inline fun <T> readOrNull(rethrow: (Exception) -> Boolean, read: () -> T): T? {
        return try {
            read()
        } catch (failure: Exception) {
            if (failure is CancellationException || rethrow(failure)) throw failure
            null
        }
    }

    private inline fun buildFields(block: FieldsBuilder.() -> Unit): List<EmergencyRecoveryField> =
        FieldsBuilder().apply(block).fields

    /** The six-field meta-account payload written before TON (database 69). */
    private object MetaAccountSecretsV69 : Schema<MetaAccountSecretsV69>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()
        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()
        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }

    private companion object {
        val rootKey = Regex("^(\\d+):(ACCESS_SECRETS|SUBSTRATE_SECRETS|ETHEREUM_SECRETS|TON_SECRETS)$")
        val chainAccountKey = Regex("^(\\d+):([0-9a-f]+):ACCESS_SECRETS$")
        val legacyQuarantine = Regex("^legacy_v(1|04)_(?:meta_(\\d+)_)?public_[0-9a-f]+$")
        const val LEGACY_V1_PREFIX = "security_source_"

        val SCANNED_PREFIXES = (0..9).map(Int::toString).toSet() + setOf(
            WalletSecretQuarantine.KEY_PREFIX,
            WalletSecretArchive.KEY_PREFIX,
            LEGACY_V1_PREFIX,
            "private_",
            "seed_",
            "entropy_",
            "derivation_"
        )
        val STORED_KEY_ORDER = compareBy<StoredKey>(
            { it.walletId ?: Long.MAX_VALUE },
            { it.state.ordinal },
            { it.kind.ordinal },
            { it.storageKey }
        )
        const val MAX_PAGE_SIZE = 64
        const val MAX_KEYS = 8_192
        const val MAX_KEY_BYTES = 1_024
        const val MAX_TOTAL_KEY_BYTES = 1_048_576
    }
}

private fun ByteArray.strictUtf8(): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

/** Where a stored value currently is. */
enum class EmergencyRecoveryState {
    /** In use for signing. */
    ACTIVE,

    /** Moved aside, unchanged, because it failed a check; never used for signing. */
    SET_ASIDE,

    /** Kept after conversion or after its wallet was removed; never used for signing. */
    ARCHIVED
}

enum class EmergencyRecoveryKeyKind {
    SUBSTRATE,
    ETHEREUM,
    TON,

    /** A wallet key in the format used before 4.0 (Substrate and Ethereum together). */
    LEGACY_WALLET,

    /** A key imported for one network only. */
    NETWORK_ACCOUNT,

    /** A key in the 1.x format. */
    LEGACY_V1,

    /** A key in the 0.4 format. */
    LEGACY_V04,

    /** A 0.4 seed, entropy or path value, kept as text. */
    LEGACY_V04_TEXT
}

enum class EmergencyRecoveryFieldType {
    RECOVERY_PHRASE,
    TON_PHRASE,
    ENTROPY,
    RAW_SEED,
    DERIVATION_PATH,
    ETHEREUM_DERIVATION_PATH,
    PRIVATE_KEY,
    ETHEREUM_PRIVATE_KEY,
    TON_PRIVATE_KEY,
    NONCE,

    /** The stored value exactly as found, when no known format reads it. */
    STORED_DATA
}

data class EmergencyRecoveryField(
    val type: EmergencyRecoveryFieldType,
    val value: String
) {
    override fun toString(): String = "EmergencyRecoveryField(type=$type)"
}

data class EmergencyRecoveryEntry(
    /** The preference key the value is stored under; a name, never a value. */
    val storageKey: String,
    val walletId: Long?,
    val state: EmergencyRecoveryState,
    val kind: EmergencyRecoveryKeyKind,
    /** False when the stored value could not be decrypted on this device. */
    val readable: Boolean,
    val fields: List<EmergencyRecoveryField>
) {
    override fun toString(): String =
        "EmergencyRecoveryEntry(storageKey=$storageKey, state=$state, kind=$kind, readable=$readable)"
}

/** Names and classifications only; contains no decrypted value. */
data class EmergencyRecoveryKeyReference(
    val storageKey: String,
    val walletId: Long?,
    val state: EmergencyRecoveryState,
    val kind: EmergencyRecoveryKeyKind
)

data class EmergencyRecoveryPage(
    val keys: List<EmergencyRecoveryKeyReference>,
    val nextStorageKey: String?
)

class EmergencyRecoveryPageChangedException : IllegalStateException("The recovery inventory changed; refresh the first page")
