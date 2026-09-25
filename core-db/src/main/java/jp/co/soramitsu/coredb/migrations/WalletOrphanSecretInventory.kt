package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.common.data.storage.encrypt.requireSnapshotMovesReady
import jp.co.soramitsu.fearless_utils.extensions.toHexString

/**
 * Finds active wallet secrets from their preference-key contract rather than
 * deriving prefixes only from rows which still exist in Room.
 *
 * An absent owner is ambiguous: the database deletion may be legitimate, or
 * it may be the surviving side of an interrupted cross-store mutation. The
 * inventory therefore never decodes, deletes, or accepts that signing/session
 * material. It atomically moves the exact ciphertext to quarantine and
 * publishes a non-secret recovery marker.
 */
@Singleton
class WalletOrphanSecretInventory internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val limits: WalletOrphanSecretInventoryLimits
) {

    @Inject
    constructor(
        encryptedPreferences: EncryptedPreferences
    ) : this(
        encryptedPreferences = encryptedPreferences,
        limits = WalletOrphanSecretInventoryLimits.PRODUCTION
    )

    internal constructor(
        encryptedPreferences: EncryptedPreferences,
        rowLimits: WalletMigrationRowLimits
    ) : this(
        encryptedPreferences = encryptedPreferences,
        limits = WalletOrphanSecretInventoryLimits(
            maximumWalletRows = rowLimits.maxWalletRows,
            maximumChainAccountRows = rowLimits.maxChainAccountRows,
            maximumTonConnectionRows =
                WalletOrphanSecretInventoryLimits.PRODUCTION
                    .maximumTonConnectionRows,
            maximumCandidateKeys =
                WalletOrphanSecretInventoryLimits.PRODUCTION
                    .maximumCandidateKeys,
            maximumKeyBytes =
                WalletOrphanSecretInventoryLimits.PRODUCTION.maximumKeyBytes,
            maximumTotalKeyBytes =
                WalletOrphanSecretInventoryLimits.PRODUCTION
                    .maximumTotalKeyBytes
        )
    )

    /**
     * Current-schema startup reconciliation. Mutation journals must be
     * reconciled before this is called, so no active journal owns a candidate.
     *
     * @return true when this call found and durably quarantined an orphan.
     */
    fun reconcile(database: SupportSQLiteDatabase): Boolean {
        encryptedPreferences.requireDurableStorageHealthy()
        val plan = prepare(
            database = database,
            excludedActiveKeys = emptySet(),
            includeTonConnectScopedKeys = true
        )
        plan.requireReady()
        plan.commit()
        return plan.hasOrphans
    }

    /**
     * Migration integration keeps this plan read-only until every other wallet
     * action has also completed its preparation pass.
     */
    internal fun prepare(
        database: SupportSQLiteDatabase,
        excludedActiveKeys: Set<String>,
        includeTonConnectScopedKeys: Boolean
    ): WalletOrphanSecretRecoveryPlan {
        val walletMetaIds = readWalletMetaIds(database)
        val chainAccountSecretKeys = readChainAccountSecretKeys(database)
        val tonConnectScopedSecretKeys = if (
            includeTonConnectScopedKeys
        ) {
            readTonConnectScopedSecretKeys(
                database = database,
                walletMetaIds = walletMetaIds
            )
        } else {
            emptySet()
        }
        val candidates = readBoundedCandidates()
        val orphanActiveKeys = linkedMapOf<String, Long>()

        candidates.forEach { activeKey ->
            if (activeKey in excludedActiveKeys) return@forEach
            val owner = WalletActiveSecretKeyParser.parse(activeKey)
            if (owner == null) {
                if (
                    WalletActiveSecretKeyParser
                        .looksLikeMalformedSensitiveNamespace(activeKey)
                ) {
                    throw WalletRecoveryStateIntegrityException(
                        "A wallet secret uses a malformed active namespace"
                    )
                }
                return@forEach
            }
            val hasOwner = when (owner) {
                is WalletActiveSecretOwner.Root ->
                    owner.metaId in walletMetaIds

                is WalletActiveSecretOwner.ChainAccount ->
                    owner.metaId in walletMetaIds &&
                        activeKey in chainAccountSecretKeys

                is WalletActiveSecretOwner.TonConnectScoped -> {
                    if (!includeTonConnectScopedKeys) {
                        return@forEach
                    }
                    owner.metaId in walletMetaIds &&
                        activeKey in tonConnectScopedSecretKeys
                }
            }
            if (!hasOwner) {
                check(orphanActiveKeys.put(activeKey, owner.metaId) == null) {
                    "Wallet orphan-secret inventory contains duplicate keys"
                }
            }
        }

        return prepareRecoveryPlan(orphanActiveKeys)
    }

    private fun readWalletMetaIds(
        database: SupportSQLiteDatabase
    ): Set<Long> {
        val boundedId = boundedIntegerProjection(
            column = "id",
            alias = BOUNDED_WALLET_ID
        )
        return buildSet {
            database.query(
                "SELECT $boundedId FROM meta_accounts ORDER BY rowid ASC " +
                    "LIMIT ${limits.maximumWalletRows + 1}"
            ).use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    if (rows == limits.maximumWalletRows) {
                        throw WalletPublicIdentityIntegrityException(
                            "Wallet rows exceed the safe orphan-inventory limit"
                        )
                    }
                    rows += 1
                    val bounded = cursor.readBoundedInteger(
                        BOUNDED_WALLET_ID
                    )
                    val metaId = bounded.value
                    if (
                        !bounded.hasExpectedStorageClass ||
                        metaId == null ||
                        metaId <= 0L
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A wallet row has no safe positive integer id"
                        )
                    }
                    if (!add(metaId)) {
                        throw WalletPublicIdentityIntegrityException(
                            "Wallet rows contain a duplicate owner id"
                        )
                    }
                }
            }
        }
    }

    private fun readChainAccountSecretKeys(
        database: SupportSQLiteDatabase
    ): Set<String> {
        val boundedMetaId = boundedIntegerProjection(
            column = "metaId",
            alias = BOUNDED_CHAIN_META_ID
        )
        val boundedAccountId = boundedBlobProjection(
            column = "accountId",
            alias = BOUNDED_CHAIN_ACCOUNT_ID,
            maxBytes = MAX_CHAIN_ACCOUNT_ID_BYTES
        )
        return buildSet {
            database.query(
                "SELECT $boundedMetaId, $boundedAccountId " +
                    "FROM chain_accounts ORDER BY rowid ASC " +
                    "LIMIT ${limits.maximumChainAccountRows + 1}"
            ).use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    if (rows == limits.maximumChainAccountRows) {
                        throw WalletPublicIdentityIntegrityException(
                            "Chain-account rows exceed the safe orphan-inventory limit"
                        )
                    }
                    rows += 1
                    val boundedOwner = cursor.readBoundedInteger(
                        BOUNDED_CHAIN_META_ID
                    )
                    val metaId = boundedOwner.value
                    if (
                        !boundedOwner.hasExpectedStorageClass ||
                        metaId == null ||
                        metaId <= 0L
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A chain-account row has no safe positive wallet id"
                        )
                    }
                    val boundedIdentity = cursor.readBoundedBlob(
                        alias = BOUNDED_CHAIN_ACCOUNT_ID,
                        maxBytes = MAX_CHAIN_ACCOUNT_ID_BYTES
                    )
                    val accountId = boundedIdentity.value
                    if (
                        !boundedIdentity.hasExpectedStorageClass ||
                        boundedIdentity.isOversized ||
                        accountId == null
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A chain-account row has no safe bounded account id"
                        )
                    }
                    // An empty stale cache identity cannot own a canonical
                    // active chain-secret namespace. The integrity migration
                    // separately traverses the row and leaves it untouched
                    // when no such secret exists; malformed lookalike keys
                    // still fail closed in readBoundedCandidates().
                    if (accountId.isEmpty()) continue
                    add(
                        "$metaId:${accountId.toHexString()}:" +
                            ACCESS_SECRET_SUFFIX
                    )
                }
            }
        }
    }

    private fun readBoundedCandidates(): Set<String> {
        val candidates = try {
            encryptedPreferences.keysWithPrefixes(
                prefixes = ACTIVE_SECRET_PREFIXES,
                maxResultCount = limits.maximumCandidateKeys,
                maxKeyBytes = limits.maximumKeyBytes,
                maxTotalKeyBytes = limits.maximumTotalKeyBytes,
                failOnOversizedMatch = true
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: WalletRecoveryStateIntegrityException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletRecoveryStateIntegrityException(
                "Wallet orphan-secret key inventory exceeds its safe bounds",
                failure
            )
        }

        if (candidates.size > limits.maximumCandidateKeys) {
            throw WalletRecoveryStateIntegrityException(
                "Wallet orphan-secret inventory exceeds its key-count bound"
            )
        }
        var totalKeyBytes = 0L
        candidates.forEach { key ->
            if (ACTIVE_SECRET_PREFIXES.none(key::startsWith)) {
                throw WalletRecoveryStateIntegrityException(
                    "A wallet orphan-secret key escaped its requested namespace"
                )
            }
            val keyBytes = key.toByteArray(Charsets.UTF_8).size
            if (keyBytes !in 1..limits.maximumKeyBytes) {
                throw WalletRecoveryStateIntegrityException(
                    "A wallet orphan-secret key exceeds its size bound"
                )
            }
            if (
                totalKeyBytes >
                limits.maximumTotalKeyBytes.toLong() - keyBytes
            ) {
                throw WalletRecoveryStateIntegrityException(
                    "Wallet orphan-secret key names exceed their total bound"
                )
            }
            totalKeyBytes += keyBytes
        }
        return candidates.toSortedSet()
    }

    private fun readTonConnectScopedSecretKeys(
        database: SupportSQLiteDatabase,
        walletMetaIds: Set<Long>
    ): Set<String> {
        val boundedMetaId = boundedIntegerProjection(
            column = "metaId",
            alias = BOUNDED_TON_META_ID
        )
        val boundedUrl = boundedTextProjection(
            column = "url",
            alias = BOUNDED_TON_URL,
            maxBytes = TonConnectStorageKeys.MAX_URL_BYTES
        )
        val boundedSource = boundedTextProjection(
            column = "source",
            alias = BOUNDED_TON_SOURCE,
            maxBytes = MAX_TON_SOURCE_BYTES
        )
        return buildSet {
            database.query(
                "SELECT $boundedMetaId, $boundedUrl, $boundedSource " +
                    "FROM ton_connection ORDER BY rowid ASC " +
                    "LIMIT ${limits.maximumTonConnectionRows + 1}"
            ).use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    if (rows == limits.maximumTonConnectionRows) {
                        throw WalletPublicIdentityIntegrityException(
                            "TON connection rows exceed the safe orphan-inventory limit"
                        )
                    }
                    rows += 1
                    val boundedOwner = cursor.readBoundedInteger(
                        BOUNDED_TON_META_ID
                    )
                    val metaId = boundedOwner.value
                    if (
                        !boundedOwner.hasExpectedStorageClass ||
                        metaId == null ||
                        metaId <= 0L ||
                        metaId !in walletMetaIds
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A TON connection row has no safe extant wallet owner"
                        )
                    }
                    val boundedUrlValue = cursor.readBoundedText(
                        alias = BOUNDED_TON_URL,
                        maxBytes = TonConnectStorageKeys.MAX_URL_BYTES
                    )
                    val url = boundedUrlValue.value
                    if (
                        !boundedUrlValue.hasExpectedStorageClass ||
                        boundedUrlValue.isOversized ||
                        url == null
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A TON connection row has no safe bounded URL"
                        )
                    }
                    val boundedSourceValue = cursor.readBoundedText(
                        alias = BOUNDED_TON_SOURCE,
                        maxBytes = MAX_TON_SOURCE_BYTES
                    )
                    val source = boundedSourceValue.value
                    if (
                        !boundedSourceValue.hasExpectedStorageClass ||
                        boundedSourceValue.isOversized ||
                        source == null
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A TON connection row has no safe bounded source"
                        )
                    }
                    val scopedKey = try {
                        TonConnectStorageKeys.scoped(
                            metaId = metaId,
                            url = url,
                            source = source
                        )
                    } catch (failure: IllegalArgumentException) {
                        throw WalletPublicIdentityIntegrityException(
                            "A TON connection row has a malformed identity",
                            failure
                        )
                    }
                    if (!add(scopedKey)) {
                        throw WalletPublicIdentityIntegrityException(
                            "TON connection rows share a scoped secret owner"
                        )
                    }
                }
            }
        }
    }

    private fun prepareRecoveryPlan(
        orphanActiveKeys: Map<String, Long>
    ): WalletOrphanSecretRecoveryPlan {
        val markerStates =
            linkedMapOf<String, EncryptedPreferenceSnapshot?>()
        val markerValues = linkedMapOf<String, String>()
        val snapshotMoves = mutableListOf<EncryptedPreferenceSnapshotMove>()

        orphanActiveKeys.forEach { (activeKey, metaId) ->
            if (!encryptedPreferences.hasKey(activeKey)) {
                throw WalletSecretConcurrentMutationException(
                    "An orphan wallet secret disappeared during inventory"
                )
            }
            val activeSnapshot = checkNotNull(
                encryptedPreferences.getDecryptedStringSnapshot(activeKey)
            ) {
                "An orphan wallet secret disappeared during inventory"
            }
            val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
            val markerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeKey
            )
            val markerSnapshot = if (
                encryptedPreferences.hasKey(markerKey)
            ) {
                checkNotNull(
                    encryptedPreferences.getDecryptedStringSnapshot(markerKey)
                ) {
                    "An orphan wallet recovery marker disappeared during inventory"
                }.also {
                    if (
                        it.plaintext !=
                        WalletPublicIdentityRecovery.MARKER_VALUE
                    ) {
                        throw WalletRecoveryStateIntegrityException(
                            "An orphan wallet recovery marker is invalid"
                        )
                    }
                }
            } else {
                null
            }
            if (markerKey in markerStates) {
                throw WalletRecoveryStateIntegrityException(
                    "Orphan wallet secrets share a recovery marker"
                )
            }
            markerStates[markerKey] = markerSnapshot
            if (markerSnapshot == null) {
                markerValues[markerKey] =
                    WalletPublicIdentityRecovery.MARKER_VALUE
            }
            snapshotMoves += EncryptedPreferenceSnapshotMove(
                sourceKey = activeKey,
                destinationKey = quarantineKey,
                expectedSnapshot = activeSnapshot
            )
        }

        return WalletOrphanSecretRecoveryPlan(
            encryptedPreferences = encryptedPreferences,
            expectedMarkerStates = markerStates,
            markerValuesToPut = markerValues,
            snapshotMoves = snapshotMoves
        )
    }

    private companion object {
        const val ACCESS_SECRET_SUFFIX = "ACCESS_SECRETS"
        const val MAX_CHAIN_ACCOUNT_ID_BYTES = 64
        const val MAX_TON_SOURCE_BYTES = 3
        const val BOUNDED_WALLET_ID = "orphanInventoryWalletId"
        const val BOUNDED_CHAIN_META_ID = "orphanInventoryChainMetaId"
        const val BOUNDED_CHAIN_ACCOUNT_ID =
            "orphanInventoryChainAccountId"
        const val BOUNDED_TON_META_ID = "orphanInventoryTonMetaId"
        const val BOUNDED_TON_URL = "orphanInventoryTonUrl"
        const val BOUNDED_TON_SOURCE = "orphanInventoryTonSource"

        val ACTIVE_SECRET_PREFIXES: Set<String> = buildSet {
            ('0'..'9').mapTo(this, Char::toString)
            add("TON_CONNECT_SCOPED_V1_")
        }
    }
}

internal data class WalletOrphanSecretInventoryLimits(
    val maximumWalletRows: Int,
    val maximumChainAccountRows: Int,
    val maximumTonConnectionRows: Int,
    val maximumCandidateKeys: Int,
    val maximumKeyBytes: Int,
    val maximumTotalKeyBytes: Int
) {
    init {
        require(maximumWalletRows in 1 until Int.MAX_VALUE)
        require(maximumChainAccountRows in 1 until Int.MAX_VALUE)
        require(maximumTonConnectionRows in 1 until Int.MAX_VALUE)
        require(maximumCandidateKeys in 1..8_192)
        require(maximumKeyBytes in 1..1_024)
        require(maximumTotalKeyBytes in maximumKeyBytes..1_048_576)
    }

    companion object {
        val PRODUCTION = WalletOrphanSecretInventoryLimits(
            maximumWalletRows = 8_192,
            maximumChainAccountRows = 131_072,
            // This is also the encrypted-key inventory ceiling. A database
            // above it cannot have every scoped private key safely reconciled
            // in one bounded startup pass.
            maximumTonConnectionRows = 8_192,
            maximumCandidateKeys = 8_192,
            maximumKeyBytes = 512,
            maximumTotalKeyBytes = 1_048_576
        )
    }
}

internal class WalletOrphanSecretRecoveryPlan(
    private val encryptedPreferences: EncryptedPreferences,
    private val expectedMarkerStates:
    Map<String, EncryptedPreferenceSnapshot?>,
    private val markerValuesToPut: Map<String, String>,
    private val snapshotMoves: List<EncryptedPreferenceSnapshotMove>
) {

    val hasOrphans: Boolean
        get() = snapshotMoves.isNotEmpty()

    fun requireReady() {
        encryptedPreferences.requireSnapshotMovesReady(snapshotMoves)
        expectedMarkerStates.forEach { (markerKey, expectedSnapshot) ->
            val actualSnapshot = if (
                encryptedPreferences.hasKey(markerKey)
            ) {
                encryptedPreferences.getDecryptedStringSnapshot(markerKey)
                    ?: throw WalletSecretConcurrentMutationException(
                        "An orphan wallet recovery marker disappeared"
                    )
            } else {
                null
            }
            val matches = if (expectedSnapshot == null) {
                actualSnapshot == null
            } else {
                actualSnapshot != null &&
                    expectedSnapshot.matchesExactSnapshot(actualSnapshot)
            }
            if (!matches) {
                throw WalletSecretConcurrentMutationException(
                    "An orphan wallet recovery marker changed during inventory"
                )
            }
        }
    }

    fun commit() {
        if (!hasOrphans) return
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = expectedMarkerStates,
            valuesToPut = markerValuesToPut,
            keysToRemove = emptySet(),
            snapshotMoves = snapshotMoves
        )
    }
}

internal sealed interface WalletActiveSecretOwner {
    val metaId: Long

    data class Root(
        override val metaId: Long
    ) : WalletActiveSecretOwner

    data class ChainAccount(
        override val metaId: Long
    ) : WalletActiveSecretOwner

    data class TonConnectScoped(
        override val metaId: Long
    ) : WalletActiveSecretOwner
}

internal object WalletActiveSecretKeyParser {

    fun parse(key: String): WalletActiveSecretOwner? {
        if (key.startsWith(TON_CONNECT_SCOPED_PREFIX)) {
            return parseTonConnectScoped(key)
        }

        val firstSeparator = key.indexOf(':')
        if (firstSeparator <= 0) return null
        val metaId = canonicalPositiveLong(
            key.substring(0, firstSeparator)
        ) ?: return null
        val namespace = key.substring(firstSeparator + 1)
        if (namespace in ROOT_SECRET_SUFFIXES) {
            return WalletActiveSecretOwner.Root(metaId)
        }
        if (!namespace.endsWith(CHAIN_SECRET_SUFFIX)) return null
        val accountIdHex = namespace.removeSuffix(CHAIN_SECRET_SUFFIX)
        if (
            accountIdHex.length !in MIN_CHAIN_ACCOUNT_HEX_CHARS..
            MAX_CHAIN_ACCOUNT_HEX_CHARS ||
            accountIdHex.length % 2 != 0 ||
            accountIdHex.any { it !in LOWERCASE_HEX }
        ) {
            return null
        }
        return WalletActiveSecretOwner.ChainAccount(metaId)
    }

    fun looksLikeMalformedSensitiveNamespace(key: String): Boolean {
        if (key.startsWith(TON_CONNECT_SCOPED_PREFIX)) return true
        val firstCharacter = key.firstOrNull() ?: return false
        if (firstCharacter !in '0'..'9') return false
        return key.contains(
            other = "SECRET",
            ignoreCase = true
        )
    }

    private fun parseTonConnectScoped(
        key: String
    ): WalletActiveSecretOwner? {
        if (!TonConnectStorageKeys.isScopedKey(key)) return null
        val suffix = key.removePrefix(TON_CONNECT_SCOPED_PREFIX)
        val separator = suffix.indexOf('_')
        if (separator <= 0) return null
        val metaId = canonicalPositiveLong(
            suffix.substring(0, separator)
        ) ?: return null
        return WalletActiveSecretOwner.TonConnectScoped(metaId)
    }

    private fun canonicalPositiveLong(encoded: String): Long? {
        val value = encoded.toLongOrNull() ?: return null
        return value.takeIf {
            it > 0L && it.toString() == encoded
        }
    }

    private const val TON_CONNECT_SCOPED_PREFIX =
        "TON_CONNECT_SCOPED_V1_"
    private const val CHAIN_SECRET_SUFFIX = ":ACCESS_SECRETS"
    private const val MIN_CHAIN_ACCOUNT_HEX_CHARS = 40
    private const val MAX_CHAIN_ACCOUNT_HEX_CHARS = 128
    private val ROOT_SECRET_SUFFIXES = setOf(
        "ACCESS_SECRETS",
        "SUBSTRATE_SECRETS",
        "ETHEREUM_SECRETS",
        "TON_SECRETS"
    )
    private val LOWERCASE_HEX = ('0'..'9') + ('a'..'f')
}
