package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.domain.recovery.VerifiedWalletSecret
import jp.co.soramitsu.account.impl.domain.recovery.WalletIdentityVerifier
import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyIdentity
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.encrypt.planArchiveMove
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretEnvelopes
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretLockedException
import kotlinx.coroutines.sync.withLock

/**
 * Repairs one unusable key with one durable preference commit. Old bytes move
 * to the retained archive in that same commit; Room and unrelated keys never
 * change. Authentication belongs to the calling recovery flow.
 */
class WalletSecretRebindService(
    private val preferences: EncryptedPreferences,
    private val identities: WalletKeyIdentityReader,
    private val verifier: WalletIdentityVerifier,
    private val journal: WalletSecretMutationJournalStore
) {
    enum class Result { RESTORED, ALREADY_USABLE }

    /**
     * [recoveredKey] is SK from a backup proof when the PIN is forgotten; without
     * it the session's SK opens a sealed key. A sealed key that neither opens is
     * never archived or replaced: [WalletSecretLockedException] leaves it as it is.
     */
    suspend fun rebind(
        metaId: Long,
        candidate: VerifiedWalletSecret,
        recoveredKey: WalletSecretKeyring.OpenedKey? = null
    ): Result =
        WalletCrossStoreMutationMutex.instance.withLock {
            require(metaId > 0) { "A wallet id is required" }
            // The intent alone: staged values may be sealed while the PIN is forgotten.
            check(journal.loadIntent() == null) { "A pending wallet change must finish before recovery" }
            val identity = identities.read(metaId, candidate.scope)
            require(identity.scope == candidate.scope) { "The recovery target changed" }
            // The identity may have changed since the recovery screen derived its candidate.
            val replacement = verifier.validate(identity, candidate.encoded)
            val activeKey = activeKey(metaId, candidate.scope)
            val active = preferences.getDecryptedStringSnapshot(activeKey)
            // Opened through SK before it is judged; a locked sealed key throws here and stays put.
            val activePlaintext = active?.plaintext?.let { open(it, recoveredKey) }
            val keepExisting = activePlaintext != null && isUsable(identity, activePlaintext)
            val quarantine = WalletSecretQuarantine.keyFor(activeKey)
            val quarantined = preferences.getDecryptedStringSnapshot(quarantine)
            val marker = WalletPublicIdentityRecovery.keyFor(metaId, activeKey)
            val marked = preferences.getDecryptedStringSnapshot(marker)
            if (keepExisting && quarantined == null && marked == null) return@withLock Result.ALREADY_USABLE

            val states = linkedMapOf<String, EncryptedPreferenceSnapshot?>(activeKey to active)
            val moves = mutableListOf<EncryptedPreferenceSnapshotMove>()
            if (active != null && !keepExisting) {
                moves += preferences.planArchiveMove(activeKey, active).copy(replaceSource = true)
            }
            quarantined?.let { snapshot ->
                moves += preferences.planArchiveMove(
                    sourceKey = quarantine,
                    expectedSnapshot = snapshot,
                    archiveNameKey = activeKey,
                    reservedDestinations = moves.mapTo(mutableSetOf()) { it.destinationKey }
                )
            }
            states[marker] = marked
            // Shared legacy ACCESS payloads remain retained until startup can
            // reconcile all of their scopes. Restoring Substrate must never
            // erase another still-waiting Ethereum key from the same payload.
            val committed = preferences.replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates = states,
                valuesToPut = if (keepExisting) emptyMap() else mapOf(activeKey to replacement),
                keysToRemove = setOf(marker),
                snapshotMoves = moves
            )
            if (!committed) throw WalletSecretConcurrentMutationException("Wallet data changed during recovery")
            val stored = checkNotNull(preferences.getDecryptedString(activeKey)) { "The restored key could not be read" }
            val expected = if (keepExisting) verifier.validate(identity, checkNotNull(activePlaintext)) else replacement
            check(verifier.validate(identity, open(stored, recoveredKey)) == expected) { "The restored key did not pass its read-back check" }
            Result.RESTORED
        }

    private fun open(stored: String, recoveredKey: WalletSecretKeyring.OpenedKey?): String =
        if (recoveredKey != null) {
            WalletSecretEnvelopes.open(stored, recoveredKey.keyId, recoveredKey.key)
        } else {
            WalletSecretEnvelopes.open(stored)
        }

    private fun isUsable(identity: WalletKeyIdentity, encoded: String): Boolean = try {
        verifier.validate(identity, encoded)
        true
    } catch (_: WalletRootSecretCorruptionException) {
        false
    } catch (_: ChainAccountSecretCorruptionException) {
        false
    }

    private fun activeKey(metaId: Long, scope: WalletSecretScope): String = when (scope) {
        WalletSecretScope.Substrate -> "$metaId:SUBSTRATE_SECRETS"
        WalletSecretScope.Ethereum -> "$metaId:ETHEREUM_SECRETS"
        WalletSecretScope.Ton -> "$metaId:TON_SECRETS"
        is WalletSecretScope.ChainAccount -> {
            require(scope.hasSecretNamespace) { "This account has no independent key namespace" }
            "$metaId:${scope.accountIdHex}:ACCESS_SECRETS"
        }
    }
}
