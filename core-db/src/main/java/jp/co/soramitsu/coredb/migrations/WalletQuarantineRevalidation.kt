package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidation
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.planArchiveMove
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import kotlinx.coroutines.CancellationException

/**
 * Re-evaluates, on every start, the recovery state earlier builds wrote, with
 * the validators of this build (addendum U0.3). It is preference-only,
 * idempotent, and every change is an exact compare-and-set. Recovery names
 * are visited without a global count gate; each owner is read independently
 * using bounded cells and pages. This does not promise a fixed startup time:
 *
 * - A quarantine or marker whose wallet row no longer exists (a wallet
 *   deleted on 4.x, flagged by the orphan inventory of an internal build)
 *   moves to [WalletSecretArchive]; its non-secret marker is removed.
 * - A public-identity marker of an existing wallet is removed when the
 *   identity it flagged and the key stored for it now pass this build's
 *   checks, or when nothing is stored for it any more; a legacy payload it
 *   kept from conversion is then converted. A marker that still applies stays
 *   and affects only its own key.
 * - A quarantined key of an existing wallet whose active entry is absent
 *   moves back when it now proves the wallet's public identity; a legacy
 *   `ACCESS_SECRETS` payload is converted the way the upgrade converts it.
 * - A quarantine that sits next to an active key proving the same identity
 *   is stale and moves to the archive.
 * - An archived key of an existing wallet moves back when the active entry
 *   is absent and nothing else claims it, so archiving is never one-way.
 *
 * Nothing is ever deleted or overwritten. A value that still fails stays
 * exactly where it is, and no outcome of this pass blocks startup: a failed
 * item is skipped and retried on the next start. Only secure-storage
 * failures, which every other startup step reports anyway, propagate.
 */
@Singleton
class WalletQuarantineRevalidation internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val walletRootSecretValidation: WalletRootSecretValidation,
    private val chainAccountSecretValidation: ChainAccountSecretValidation,
    private val limits: WalletOrphanSecretInventoryLimits
) {

    @Inject
    constructor(
        encryptedPreferences: EncryptedPreferences
    ) : this(
        encryptedPreferences = encryptedPreferences,
        walletRootSecretValidation = WalletRootSecretValidator,
        chainAccountSecretValidation = ChainAccountSecretValidator,
        limits = WalletOrphanSecretInventoryLimits.PRODUCTION
    )

    fun reconcile(database: SupportSQLiteDatabase): WalletQuarantineRevalidationReport {
        encryptedPreferences.requireDurableStorageHealthy()
        val report = ReportBuilder()

        // Each pass observes a names-only storage snapshot. There is no global
        // wallet/secret count gate, and no unrelated identity is materialized.
        fun visit(prefix: String, action: (String) -> Outcome) {
            report.attempt {
                encryptedPreferences.forEachKeyWithPrefixes(setOf(prefix), limits.maximumKeyBytes) { key ->
                    report.attempt { action(key) }
                }
                Outcome.UNCHANGED
            }
        }
        visit(WalletPublicIdentityRecovery.KEY_PREFIX) { reevaluateMarker(database, it) }
        visit(WalletSecretQuarantine.KEY_PREFIX) { reconcileQuarantine(database, it) }
        visit(WalletPublicIdentityRecovery.KEY_PREFIX) { clearOrphanMarker(database, it) }
        visit(WalletSecretArchive.KEY_PREFIX) { restoreArchive(database, it) }
        return report.build()
    }

    private fun readState(
        database: SupportSQLiteDatabase,
        metaId: Long,
        activeKey: String? = null,
        markerKey: String? = null
    ): RevalidationState {
        val identity = readBoundedWalletPublicIdentity(database, includeTonPublicKey = true, metaId = metaId)
        val wallets = identity?.let { mapOf(metaId to it) }.orEmpty()
        val rootMarker = RootEcosystem.entries.any {
            WalletPublicIdentityRecovery.keyFor(metaId, "$metaId:${it.suffix}") == markerKey
        }
        val needsChain = RecoveryKeyOwner.of(activeKey.orEmpty()) is RecoveryKeyOwner.ChainAccount ||
            markerKey != null && !rootMarker
        return RevalidationState(
            wallets = wallets,
            chainAccounts = if (identity != null && needsChain) {
                readChainAccounts(database, metaId, activeKey, markerKey)
            } else {
                emptyMap()
            },
            tonConnectScopedKeys = if (identity != null && RecoveryKeyOwner.of(activeKey.orEmpty()) is RecoveryKeyOwner.TonConnectScoped) {
                readTonConnectKey(database, metaId, checkNotNull(activeKey))
            } else {
                emptySet()
            }
        )
    }

    private fun reconcileQuarantine(
        database: SupportSQLiteDatabase,
        quarantineKey: String
    ): Outcome {
        if (!encryptedPreferences.hasKey(quarantineKey)) return Outcome.UNCHANGED
        val activeKey = quarantineKey.removePrefix(WalletSecretQuarantine.KEY_PREFIX)
        val owner = RecoveryKeyOwner.of(activeKey) ?: return Outcome.UNCHANGED
        val state = readState(database, owner.metaId, activeKey = activeKey)
        if (!state.canJudge(owner)) return Outcome.UNCHANGED
        val snapshot = encryptedPreferences.getDecryptedStringSnapshot(quarantineKey)
            ?: return Outcome.UNCHANGED

        if (!state.ownsKey(owner, activeKey)) {
            archive(
                sourceKey = quarantineKey,
                snapshot = snapshot,
                archiveNameKey = activeKey,
                markerKey = WalletPublicIdentityRecovery.keyFor(owner.metaId, activeKey)
            )
            return Outcome.ARCHIVED
        }
        if (hasMarker(owner.metaId, activeKey)) return Outcome.UNCHANGED

        return when (owner) {
            is RecoveryKeyOwner.Root -> reconcileRootQuarantine(
                identity = state.wallets.getValue(owner.metaId),
                ecosystem = owner.ecosystem,
                quarantineKey = quarantineKey,
                activeKey = activeKey,
                snapshot = snapshot
            )

            is RecoveryKeyOwner.ChainAccount -> reconcileProvenQuarantine(
                quarantineKey = quarantineKey,
                activeKey = activeKey,
                snapshot = snapshot,
                proves = { provesChainAccount(state, activeKey, it) }
            )

            // A TON Connect session key has no public identity to prove; its
            // connection row owning the key is the evidence.
            is RecoveryKeyOwner.TonConnectScoped -> reconcileProvenQuarantine(
                quarantineKey = quarantineKey,
                activeKey = activeKey,
                snapshot = snapshot,
                proves = { true }
            )

            is RecoveryKeyOwner.LegacyV1 -> Outcome.UNCHANGED
        }
    }

    private fun reconcileRootQuarantine(
        identity: WalletPublicIdentity,
        ecosystem: RootEcosystem,
        quarantineKey: String,
        activeKey: String,
        snapshot: EncryptedPreferenceSnapshot
    ): Outcome {
        if (ecosystem == RootEcosystem.LEGACY) {
            return convertLegacy(identity, quarantineKey, snapshot)
        }
        val proven = reconcileProvenQuarantine(
            quarantineKey = quarantineKey,
            activeKey = activeKey,
            snapshot = snapshot,
            proves = { provesRoot(identity, ecosystem, it) }
        )
        if (
            proven != Outcome.UNCHANGED ||
            ecosystem == RootEcosystem.TON ||
            encryptedPreferences.hasKey(activeKey)
        ) {
            return proven
        }
        // The upgrade scopes a legacy payload whose Ethereum key failed to
        // the Ethereum quarantine; re-run that conversion with this build.
        return convertLegacy(identity, quarantineKey, snapshot)
    }

    /**
     * Moves a quarantined value back when its active entry is absent and it
     * proves its identity, or archives it when an active entry proving the
     * same identity already exists. An unreadable quarantined value (the
     * decryptor reports it as empty) is never released, but it is archived
     * like any other once a proving key is active again.
     */
    private fun reconcileProvenQuarantine(
        quarantineKey: String,
        activeKey: String,
        snapshot: EncryptedPreferenceSnapshot,
        proves: (String) -> Boolean
    ): Outcome {
        if (!encryptedPreferences.hasKey(activeKey)) {
            if (snapshot.plaintext.isEmpty() || !proves(snapshot.plaintext)) {
                return Outcome.UNCHANGED
            }
            move(quarantineKey, activeKey, snapshot)
            return Outcome.RELEASED
        }
        val active = encryptedPreferences.getDecryptedStringSnapshot(activeKey)
            ?: return Outcome.UNCHANGED
        if (active.plaintext.isEmpty() || !proves(active.plaintext)) {
            return Outcome.UNCHANGED
        }
        archive(quarantineKey, snapshot, archiveNameKey = activeKey)
        return Outcome.ARCHIVED
    }

    private fun convertLegacy(
        identity: WalletPublicIdentity,
        legacySourceKey: String,
        snapshot: EncryptedPreferenceSnapshot,
        legacyMovable: Boolean = true
    ): Outcome {
        val replacement = try {
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = snapshot.plaintext,
                identity = identity,
                walletRootSecretValidation = walletRootSecretValidation
            )
        } catch (_: WalletSecretIntegrityCorruptionException) {
            return Outcome.UNCHANGED
        }
        val plan = encryptedPreferences.planLegacySecretConversion(
            identity = identity,
            legacySourceKey = legacySourceKey,
            legacySnapshot = snapshot,
            replacement = replacement,
            walletRootSecretValidation = walletRootSecretValidation,
            legacyMovable = legacyMovable
        )
        if (plan.isNoOp) return Outcome.UNCHANGED
        plan.commit(encryptedPreferences)
        return Outcome.RELEASED
    }

    /**
     * Re-runs the identity and signing-integrity checks a marker of an
     * existing wallet stands for (addendum U0.3: internal builds 230/231 wrote
     * markers with their own validators). The marker is non-secret; removing
     * it never touches a stored value.
     */
    private fun reevaluateMarker(
        database: SupportSQLiteDatabase,
        markerKey: String
    ): Outcome {
        if (!encryptedPreferences.hasKey(markerKey)) return Outcome.UNCHANGED
        val metaId = markerKey
            .removePrefix(WalletPublicIdentityRecovery.KEY_PREFIX)
            .substringBefore(':')
            .toCanonicalPositiveLongOrNull()
            ?: return Outcome.UNCHANGED
        val state = readState(database, metaId, markerKey = markerKey)
        val identity = state.wallets[metaId] ?: return Outcome.UNCHANGED
        val activeKey = state.markedKeyCandidates(metaId).firstOrNull {
            WalletPublicIdentityRecovery.keyFor(metaId, it) == markerKey
        } ?: return Outcome.UNCHANGED
        val owner = RecoveryKeyOwner.of(activeKey) ?: return Outcome.UNCHANGED

        val active = encryptedPreferences.getDecryptedStringSnapshot(activeKey)
            ?.takeIf { encryptedPreferences.hasKey(activeKey) }
        val stored = active ?: encryptedPreferences
            .getDecryptedStringSnapshot(WalletSecretQuarantine.keyFor(activeKey))
            ?.takeIf { encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(activeKey)) }
        if (stored != null && !passesCurrentChecks(state, identity, owner, activeKey, stored.plaintext)) {
            return Outcome.UNCHANGED
        }
        val marker = encryptedPreferences.getDecryptedStringSnapshot(markerKey)
            ?: return Outcome.UNCHANGED
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = mapOf(markerKey to marker),
            valuesToPut = emptyMap(),
            keysToRemove = setOf(markerKey)
        )
        // The migration left an active legacy payload unconverted because of
        // the marker; convert it now as the upgrade would have.
        if (
            active != null &&
            owner is RecoveryKeyOwner.Root &&
            owner.ecosystem == RootEcosystem.LEGACY
        ) {
            convertLegacy(identity, activeKey, active)
        }
        return Outcome.MARKER_CLEARED
    }

    private fun passesCurrentChecks(
        state: RevalidationState,
        identity: WalletPublicIdentity,
        owner: RecoveryKeyOwner,
        activeKey: String,
        plaintext: String
    ): Boolean {
        if (plaintext.isEmpty()) return false
        return when (owner) {
            is RecoveryKeyOwner.Root -> if (owner.ecosystem == RootEcosystem.LEGACY) {
                provesIdentity {
                    WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                        encoded = plaintext,
                        identity = identity,
                        walletRootSecretValidation = walletRootSecretValidation
                    )
                }
            } else {
                provesRoot(identity, owner.ecosystem, plaintext)
            }

            is RecoveryKeyOwner.ChainAccount -> provesChainAccount(state, activeKey, plaintext)

            is RecoveryKeyOwner.TonConnectScoped,
            is RecoveryKeyOwner.LegacyV1 -> false
        }
    }

    private fun clearOrphanMarker(
        database: SupportSQLiteDatabase,
        markerKey: String
    ): Outcome {
        val metaId = markerKey
            .removePrefix(WalletPublicIdentityRecovery.KEY_PREFIX)
            .substringBefore(':')
            .toCanonicalPositiveLongOrNull()
            ?: return Outcome.UNCHANGED
        if (readBoundedWalletPublicIdentity(database, includeTonPublicKey = true, metaId = metaId) != null) {
            return Outcome.UNCHANGED
        }
        val marker = encryptedPreferences.getDecryptedStringSnapshot(markerKey)
            ?: return Outcome.UNCHANGED
        // Markers are non-secret protocol tokens: a wallet that no longer
        // exists has nothing left for one to protect.
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = mapOf(markerKey to marker),
            valuesToPut = emptyMap(),
            keysToRemove = setOf(markerKey)
        )
        return Outcome.MARKER_CLEARED
    }

    private fun restoreArchive(
        database: SupportSQLiteDatabase,
        archiveKey: String
    ): Outcome {
        if (!encryptedPreferences.hasKey(archiveKey)) return Outcome.UNCHANGED
        val activeKey = WalletSecretArchive.sourceKeyOf(archiveKey) ?: return Outcome.UNCHANGED
        val owner = RecoveryKeyOwner.of(activeKey) ?: return Outcome.UNCHANGED
        val state = readState(database, owner.metaId, activeKey = activeKey)
        if (!state.canJudge(owner) || !state.ownsKey(owner, activeKey)) {
            return Outcome.UNCHANGED
        }
        if (hasMarker(owner.metaId, activeKey)) return Outcome.UNCHANGED

        if (owner is RecoveryKeyOwner.Root && owner.ecosystem == RootEcosystem.LEGACY) {
            return restoreFromLegacyArchive(state, owner.metaId, archiveKey)
        }
        if (
            encryptedPreferences.hasKey(activeKey) ||
            encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        ) {
            return Outcome.UNCHANGED
        }
        val snapshot = encryptedPreferences.getDecryptedStringSnapshot(archiveKey)
            ?: return Outcome.UNCHANGED
        if (snapshot.plaintext.isEmpty()) return Outcome.UNCHANGED
        val proves = when (owner) {
            is RecoveryKeyOwner.Root -> provesRoot(
                identity = state.wallets.getValue(owner.metaId),
                ecosystem = owner.ecosystem,
                plaintext = snapshot.plaintext
            )

            is RecoveryKeyOwner.ChainAccount ->
                provesChainAccount(state, activeKey, snapshot.plaintext)

            is RecoveryKeyOwner.TonConnectScoped -> true
            is RecoveryKeyOwner.LegacyV1 -> false
        }
        if (!proves) return Outcome.UNCHANGED
        move(archiveKey, activeKey, snapshot)
        return Outcome.RESTORED
    }

    /**
     * A converted legacy payload stays archived as the record of what the
     * wallet held before 4.0. It is read again only when an ecosystem it can
     * serve has no active key and nothing else claims that key.
     */
    private fun restoreFromLegacyArchive(
        state: RevalidationState,
        metaId: Long,
        archiveKey: String
    ): Outcome {
        val identity = state.wallets.getValue(metaId)
        val unclaimed = { key: String ->
            !encryptedPreferences.hasKey(key) &&
                !encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(key))
        }
        val accessKey = "$metaId:ACCESS_SECRETS"
        val substrateMissing = identity.hasCompleteSubstrateIdentity &&
            unclaimed("$metaId:SUBSTRATE_SECRETS")
        val ethereumMissing = identity.hasCompleteEthereumIdentity &&
            unclaimed("$metaId:ETHEREUM_SECRETS")
        if (
            !(substrateMissing || ethereumMissing) ||
            encryptedPreferences.hasKey(accessKey) ||
            encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(accessKey))
        ) {
            return Outcome.UNCHANGED
        }
        val snapshot = encryptedPreferences.getDecryptedStringSnapshot(archiveKey)
            ?: return Outcome.UNCHANGED
        if (snapshot.plaintext.isEmpty()) return Outcome.UNCHANGED
        // The archive is never turned into recovery state: if an ecosystem
        // still fails, the payload stays archived and only what converts is
        // published.
        val converted = convertLegacy(identity, archiveKey, snapshot, legacyMovable = false)
        return if (converted == Outcome.RELEASED) Outcome.RESTORED else converted
    }

    private fun provesRoot(
        identity: WalletPublicIdentity,
        ecosystem: RootEcosystem,
        plaintext: String
    ): Boolean = provesIdentity {
        when (ecosystem) {
            RootEcosystem.SUBSTRATE -> WalletSecretIntegrityValidator.validateSubstrateV3(
                encoded = plaintext,
                identity = identity,
                walletRootSecretValidation = walletRootSecretValidation
            )

            RootEcosystem.ETHEREUM -> WalletSecretIntegrityValidator.validateEthereumV3(
                encoded = plaintext,
                identity = identity,
                walletRootSecretValidation = walletRootSecretValidation
            )

            RootEcosystem.TON -> WalletSecretIntegrityValidator.validateTonV3(
                encoded = plaintext,
                identity = identity,
                walletRootSecretValidation = walletRootSecretValidation
            )

            RootEcosystem.LEGACY -> error("A legacy payload is converted, not proven")
        }
    }

    private fun provesChainAccount(
        state: RevalidationState,
        activeKey: String,
        plaintext: String
    ): Boolean {
        val row = state.chainAccounts?.get(activeKey) ?: return false
        val cryptoType = row.cryptoTypeText?.let { text ->
            CryptoType.entries.firstOrNull { it.name == text }
        } ?: return false
        return provesIdentity {
            chainAccountSecretValidation.validateAndSanitize(
                encoded = plaintext,
                expectedAccountId = row.accountId,
                expectedPublicKey = row.publicKey,
                expectedCryptoType = cryptoType
            )
        }
    }

    /**
     * Any validation failure, of the payload or of the durable identity it is
     * judged against, means "cannot prove"; only provider failures propagate.
     */
    private inline fun provesIdentity(validate: () -> Unit): Boolean {
        return try {
            validate()
            true
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            false
        }
    }

    private fun hasMarker(metaId: Long, activeKey: String): Boolean {
        return encryptedPreferences.hasKey(
            WalletPublicIdentityRecovery.keyFor(metaId, activeKey)
        )
    }

    private fun move(
        sourceKey: String,
        destinationKey: String,
        snapshot: EncryptedPreferenceSnapshot
    ) {
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = emptyMap(),
            valuesToPut = emptyMap(),
            keysToRemove = emptySet(),
            snapshotMoves = listOf(
                EncryptedPreferenceSnapshotMove(
                    sourceKey = sourceKey,
                    destinationKey = destinationKey,
                    expectedSnapshot = snapshot
                )
            )
        )
    }

    private fun archive(
        sourceKey: String,
        snapshot: EncryptedPreferenceSnapshot,
        archiveNameKey: String,
        markerKey: String? = null
    ) {
        val marker = markerKey
            ?.takeIf(encryptedPreferences::hasKey)
            ?.let { key -> encryptedPreferences.getDecryptedStringSnapshot(key)?.let { key to it } }
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = listOfNotNull(marker).toMap(),
            valuesToPut = emptyMap(),
            keysToRemove = listOfNotNull(marker?.first).toSet(),
            snapshotMoves = listOf(
                encryptedPreferences.planArchiveMove(
                    sourceKey = sourceKey,
                    expectedSnapshot = snapshot,
                    archiveNameKey = archiveNameKey
                )
            )
        )
    }

    private fun readChainAccounts(
        database: SupportSQLiteDatabase,
        metaId: Long,
        activeKey: String?,
        markerKey: String?
    ): Map<String, ChainAccountRow>? {
        val accountProjection = boundedBlobProjection("accountId", BOUNDED_CHAIN_ACCOUNT_ID, MAX_CHAIN_ACCOUNT_ID_BYTES)
        val publicProjection = boundedBlobProjection("publicKey", BOUNDED_CHAIN_PUBLIC_KEY, MAX_CHAIN_PUBLIC_KEY_BYTES)
        val cryptoProjection = boundedTextProjection("cryptoType", BOUNDED_CHAIN_CRYPTO_TYPE, MAX_CRYPTO_TYPE_TEXT_BYTES)
        val rows = linkedMapOf<String, ChainAccountRow>()
        var unreadable = false
        var ambiguousTarget = false
        visitOwnerRows(database, "chain_accounts", metaId, "$accountProjection, $publicProjection, $cryptoProjection") { cursor ->
            val account = cursor.readBoundedBlob(BOUNDED_CHAIN_ACCOUNT_ID, MAX_CHAIN_ACCOUNT_ID_BYTES).value
            if (account == null || account.isEmpty()) {
                unreadable = true
                return@visitOwnerRows
            }
            val key = "$metaId:${account.toHexString()}:ACCESS_SECRETS"
            if (key != activeKey && WalletPublicIdentityRecovery.keyFor(metaId, key) != markerKey) return@visitOwnerRows
            if (cursor.readBoundedInteger("revalidationOwner").value != metaId) {
                ambiguousTarget = true
                return@visitOwnerRows
            }
            val row = ChainAccountRow(
                accountId = account,
                publicKey = cursor.readBoundedBlob(BOUNDED_CHAIN_PUBLIC_KEY, MAX_CHAIN_PUBLIC_KEY_BYTES).value,
                cryptoTypeText = cursor.readBoundedText(BOUNDED_CHAIN_CRYPTO_TYPE, MAX_CRYPTO_TYPE_TEXT_BYTES).value
            )
            val prior = rows[key]
            check(
                prior == null || prior.accountId.contentEquals(row.accountId) &&
                    prior.publicKey.contentEquals(row.publicKey) && prior.cryptoTypeText == row.cryptoTypeText
            ) {
                "Chain rows disagree about this secret's public identity"
            }
            rows[key] = row
        }
        if (rows.isEmpty() && !unreadable) {
            // A damaged owner id cannot prove that an unmatched key is an
            // orphan. A positive exact match remains independent of it.
            unreadable = database.query(
                "SELECT 1 FROM chain_accounts WHERE typeof(metaId) != 'integer' OR metaId <= 0 LIMIT 1"
            ).use { it.moveToFirst() }
        }
        return if (ambiguousTarget || rows.isEmpty() && unreadable) null else rows
    }

    private fun readTonConnectKey(
        database: SupportSQLiteDatabase,
        metaId: Long,
        activeKey: String
    ): Set<String>? {
        val url = boundedTextProjection("url", "revalidationTonUrl", TonConnectStorageKeys.MAX_URL_BYTES)
        val source = boundedTextProjection("source", "revalidationTonSource", 3)
        var matches = 0
        var unreadable = false
        var ambiguousTarget = false
        visitOwnerRows(database, "ton_connection", metaId, "$url, $source") { cursor ->
            val rowUrl = cursor.readBoundedText("revalidationTonUrl", TonConnectStorageKeys.MAX_URL_BYTES).value
            val rowSource = cursor.readBoundedText("revalidationTonSource", 3).value
            val key = try {
                if (rowUrl == null || rowSource == null) null else TonConnectStorageKeys.scoped(metaId, rowUrl, rowSource)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (key == null) unreadable = true
            if (key == activeKey) {
                if (cursor.readBoundedInteger("revalidationOwner").value == metaId) matches += 1 else ambiguousTarget = true
            }
        }
        if (matches == 0 && !unreadable) {
            // A damaged owner id could have belonged to this missing session.
            // It cannot justify archiving it; a positively matched session is
            // still independent of unrelated malformed owners.
            unreadable = database.query(
                "SELECT 1 FROM ton_connection WHERE typeof(metaId) != 'integer' OR metaId <= 0 LIMIT 1"
            ).use { it.moveToFirst() }
        }
        // A proving row is sufficient even when another connection is bad.
        // An unreadable row cannot justify treating a missing key as orphaned.
        return when {
            ambiguousTarget -> null
            matches == 1 -> setOf(activeKey)
            matches > 1 || unreadable -> null
            else -> emptySet()
        }
    }

    private fun visitOwnerRows(
        database: SupportSQLiteDatabase,
        table: String,
        metaId: Long,
        projections: String,
        action: (Cursor) -> Unit
    ) {
        check(table == "chain_accounts" || table == "ton_connection")
        var afterRowId: Long? = null
        val owner = boundedIntegerProjection("metaId", "revalidationOwner")
        do {
            val previous = afterRowId
            val clause = if (previous == null) "" else " AND rowid > ?"
            val args: Array<Any> = if (previous == null) arrayOf(metaId) else arrayOf(metaId, previous)
            val page = database.query(
                "SELECT rowid AS revalidationRowId, $owner, $projections FROM $table " +
                    "WHERE metaId = ?$clause ORDER BY rowid ASC LIMIT 64",
                args
            ).use { cursor ->
                visitOwnerCursor(cursor, previous, action)
            }
            afterRowId = page.first
        } while (page.second == 64)
    }

    private fun visitOwnerCursor(
        cursor: Cursor,
        previous: Long?,
        action: (Cursor) -> Unit
    ): Pair<Long?, Int> {
        var afterRowId = previous
        var count = 0
        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(cursor.getColumnIndexOrThrow("revalidationRowId"))
            check(afterRowId == null || rowId > checkNotNull(afterRowId))
            afterRowId = rowId
            count += 1
            action(cursor)
        }
        return afterRowId to count
    }

    private class RevalidationState(
        val wallets: Map<Long, WalletPublicIdentity>,
        val chainAccounts: Map<String, ChainAccountRow>?,
        /** Null when the `ton_connection` rows could not be read. */
        val tonConnectScopedKeys: Set<String>?
    ) {
        /** Whether this pass can tell who owns [owner]'s keys at all. */
        fun canJudge(owner: RecoveryKeyOwner): Boolean {
            return when (owner) {
                is RecoveryKeyOwner.ChainAccount -> chainAccounts != null
                is RecoveryKeyOwner.TonConnectScoped -> tonConnectScopedKeys != null
                else -> true
            }
        }

        /** Active keys a public-identity marker of [metaId] can name. */
        fun markedKeyCandidates(metaId: Long): List<String> {
            return RootEcosystem.entries.map { "$metaId:${it.suffix}" } +
                chainAccounts.orEmpty().keys.filter { it.startsWith("$metaId:") }
        }

        fun ownsKey(owner: RecoveryKeyOwner, activeKey: String): Boolean {
            if (owner.metaId !in wallets) return false
            return when (owner) {
                is RecoveryKeyOwner.Root,
                is RecoveryKeyOwner.LegacyV1 -> true

                is RecoveryKeyOwner.ChainAccount -> activeKey in chainAccounts.orEmpty()
                is RecoveryKeyOwner.TonConnectScoped ->
                    tonConnectScopedKeys?.contains(activeKey) == true
            }
        }
    }

    private class ChainAccountRow(
        val accountId: ByteArray,
        val publicKey: ByteArray?,
        val cryptoTypeText: String?
    )

    private enum class Outcome {
        UNCHANGED,
        RELEASED,
        RESTORED,
        ARCHIVED,
        MARKER_CLEARED
    }

    private class ReportBuilder {
        private var released = 0
        private var restored = 0
        private var archived = 0
        private var markersCleared = 0
        private var skipped = 0

        fun attempt(block: () -> Outcome) {
            val outcome = try {
                block()
            } catch (failure: WalletSecureStorageUnavailableException) {
                throw failure
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // A concurrent change or an unexpected value: leave this item
                // exactly as it is and try again on the next start.
                skipped += 1
                return
            }
            when (outcome) {
                Outcome.UNCHANGED -> Unit
                Outcome.RELEASED -> released += 1
                Outcome.RESTORED -> restored += 1
                Outcome.ARCHIVED -> archived += 1
                Outcome.MARKER_CLEARED -> markersCleared += 1
            }
        }

        fun build() = WalletQuarantineRevalidationReport(
            released = released,
            restored = restored,
            archived = archived,
            markersCleared = markersCleared,
            skipped = skipped
        )
    }

    private companion object {
        const val MAX_CHAIN_ACCOUNT_ID_BYTES = 64
        const val MAX_CHAIN_PUBLIC_KEY_BYTES = 33
        const val MAX_CRYPTO_TYPE_TEXT_BYTES = 32
        const val BOUNDED_CHAIN_META_ID = "revalidationChainMetaId"
        const val BOUNDED_CHAIN_ACCOUNT_ID = "revalidationChainAccountId"
        const val BOUNDED_CHAIN_PUBLIC_KEY = "revalidationChainPublicKey"
        const val BOUNDED_CHAIN_CRYPTO_TYPE = "revalidationChainCryptoType"
    }
}

/**
 * Informational outcome of one revalidation pass. [released] quarantines
 * moved back to active use (or converted, for a legacy payload), [restored]
 * archived keys moved back, [archived] stale or orphaned quarantines moved
 * to the archive, [markersCleared] markers of removed wallets dropped, and
 * [skipped] items were left as they were for the next start.
 */
data class WalletQuarantineRevalidationReport(
    val released: Int = 0,
    val restored: Int = 0,
    val archived: Int = 0,
    val markersCleared: Int = 0,
    val skipped: Int = 0
)

internal enum class RootEcosystem(val suffix: String) {
    LEGACY("ACCESS_SECRETS"),
    SUBSTRATE("SUBSTRATE_SECRETS"),
    ETHEREUM("ETHEREUM_SECRETS"),
    TON("TON_SECRETS")
}

/** The wallet a quarantine, marker or archive name belongs to. */
internal sealed interface RecoveryKeyOwner {
    val metaId: Long

    data class Root(
        override val metaId: Long,
        val ecosystem: RootEcosystem
    ) : RecoveryKeyOwner

    data class ChainAccount(override val metaId: Long) : RecoveryKeyOwner

    data class TonConnectScoped(override val metaId: Long) : RecoveryKeyOwner

    /** `legacy_v1_meta_<id>_public_…` and `legacy_v04_meta_<id>_public_…`. */
    data class LegacyV1(override val metaId: Long) : RecoveryKeyOwner

    companion object {
        private val legacyMetaQuarantine =
            Regex("^legacy_v(?:1|04)_meta_(\\d+)_public_[0-9a-f]+$")

        fun of(activeKey: String): RecoveryKeyOwner? {
            legacyMetaQuarantine.matchEntire(activeKey)?.let { match ->
                return match.groupValues[1].toCanonicalPositiveLongOrNull()
                    ?.let(::LegacyV1)
            }
            return when (val owner = WalletActiveSecretKeyParser.parse(activeKey)) {
                null -> null
                is WalletActiveSecretOwner.Root -> Root(
                    metaId = owner.metaId,
                    ecosystem = RootEcosystem.entries.single {
                        activeKey.endsWith(":${it.suffix}")
                    }
                )

                is WalletActiveSecretOwner.ChainAccount -> ChainAccount(owner.metaId)
                is WalletActiveSecretOwner.TonConnectScoped -> TonConnectScoped(owner.metaId)
            }
        }
    }
}

private fun String.toCanonicalPositiveLongOrNull(): Long? {
    val value = toLongOrNull() ?: return null
    return value.takeIf { it > 0L && it.toString() == this }
}
