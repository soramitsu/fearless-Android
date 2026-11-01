package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Arrival
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Outcome
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Row
import jp.co.soramitsu.account.api.domain.UniversalWalletRoot
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.holdsLegacy
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.universalWalletRoot
import jp.co.soramitsu.account.api.domain.universalWalletSlotAccount
import jp.co.soramitsu.account.impl.data.repository.WalletMutationFailureReason
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinatorException
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * The one notice for slots of a password wallet that still hold the account the words alone
 * open. Switch replaces such an account with the password-protected one only after a live
 * probe confirms it is empty; a funded or unreadable one stays, so no funds are hidden.
 *
 * A deposit can still reach the old address after that probe, so every account a slot left is
 * watched ([UniversalWalletWatchList]) and [refresh] probes it again: when only the address the
 * slot left holds funds, the slot moves back to it; when both do, or the other cannot be read,
 * the slot stays and a row reports the arrival.
 */
class UniversalWalletPasswordNoticeService(
    private val accountRepository: AccountRepository,
    private val metaAccountDao: MetaAccountDao,
    private val coordinator: WalletSecretMutationCoordinator,
    private val fundsProbe: UniversalWalletFundsProbe,
    private val preferences: Preferences,
    private val watchList: UniversalWalletWatchList = UniversalWalletWatchList(preferences)
) : UniversalWalletPasswordNotice {

    override suspend fun rows(metaId: Long): List<Row> {
        val wallet = accountRepository.getMetaAccount(metaId)
        val root = passwordRoot(metaId) ?: return emptyList()
        val watched = watchedEntries(metaId, root)
        return legacySlots(wallet, root).map { slot ->
            Row(
                slot.slot,
                slot.slot.address(slot.legacyKey),
                slot.slot.address(slot.slot.publicKey(root.mnemonic, root.passphrase)),
                arrival = watched[slot.slot]?.arrival?.takeIf { it.kind != Arrival.Kind.OLD_ADDRESS }
            )
        } + arrivalRows(wallet, watched.values)
    }

    override suspend fun switch(metaId: Long): List<Row> {
        val wallet = accountRepository.getMetaAccount(metaId)
        val root = passwordRoot(metaId) ?: return emptyList()
        val watched = watchedEntries(metaId, root)
        return legacySlots(wallet, root).map { legacy ->
            val slot = legacy.slot
            val protectedKey = slot.publicKey(root.mnemonic, root.passphrase)
            val outcome = when (probe(slot, root.mnemonic, "", legacy.legacyKey).funds) {
                UniversalWalletFundsProbe.Funds.FUNDED -> Outcome.STILL_FUNDED
                UniversalWalletFundsProbe.Funds.UNKNOWN -> Outcome.UNKNOWN
                UniversalWalletFundsProbe.Funds.EMPTY -> move(metaId, slot, legacy.chainId, legacy.legacyKey, protectedKey, root, root.passphrase)
            }
            if (outcome == Outcome.SWITCHED) {
                // The empty read may already be stale; keep checking the address the slot left.
                watchList.put(metaId, UniversalWalletWatchList.Entry(slot, legacy.chainId, legacy.legacyKey, protectedKey))
            }
            Row(
                slot,
                slot.address(legacy.legacyKey),
                slot.address(protectedKey),
                outcome,
                watched[slot]?.arrival?.takeIf { outcome != Outcome.SWITCHED && it.kind != Arrival.Kind.OLD_ADDRESS }
            )
        } + arrivalRows(wallet, watched.values)
    }

    override suspend fun refresh(metaId: Long) {
        val root = passwordRoot(metaId) ?: return
        val watched = watchedEntries(metaId, root).values.takeIf { it.isNotEmpty() } ?: return
        val wallet = accountRepository.getMetaAccount(metaId)
        for (entry in watched) {
            val inSlot = wallet.universalWalletSlotAccount(entry.slot)?.second ?: continue
            val usesOld = inSlot.contentEquals(entry.oldKey)
            if (!usesOld && !inSlot.contentEquals(entry.newKey)) continue
            val (offKey, offPassphrase) = if (usesOld) entry.newKey to root.passphrase else entry.oldKey to ""
            val off = probe(entry.slot, root.mnemonic, offPassphrase, offKey)
            val arrival = when (off.funds) {
                // Funds that left the other address need no row; the move back keeps its reason.
                UniversalWalletFundsProbe.Funds.EMPTY -> entry.arrival?.takeIf { it.kind == Arrival.Kind.OLD_ADDRESS_USED_AGAIN }
                UniversalWalletFundsProbe.Funds.UNKNOWN -> entry.arrival
                UniversalWalletFundsProbe.Funds.FUNDED -> {
                    val inSlotFunds = probe(entry.slot, root.mnemonic, if (usesOld) "" else root.passphrase, inSlot).funds
                    val moved = inSlotFunds == UniversalWalletFundsProbe.Funds.EMPTY &&
                        move(metaId, entry.slot, entry.chainId, inSlot, offKey, root, offPassphrase) == Outcome.SWITCHED
                    when {
                        moved && usesOld -> null
                        moved -> Arrival(Arrival.Kind.OLD_ADDRESS_USED_AGAIN, off.balance)
                        usesOld -> Arrival(Arrival.Kind.NEW_ADDRESS, off.balance)
                        else -> Arrival(Arrival.Kind.OLD_ADDRESS, off.balance)
                    }
                }
            }
            if (arrival?.kind != entry.arrival?.kind || arrival?.balance != entry.arrival?.balance) {
                watchList.put(metaId, entry.withArrival(arrival))
                // A new arrival is news: show the notice once more.
                if (arrival != null && arrival.kind != entry.arrival?.kind) preferences.putString(dismissedKey(metaId), null)
            }
        }
    }

    override fun isDismissed(metaId: Long): Boolean = preferences.getString(dismissedKey(metaId)) == DISMISSED

    override fun dismiss(metaId: Long) = preferences.putString(dismissedKey(metaId), DISMISSED)

    private class LegacySlot(val slot: UniversalWalletSlot, val chainId: String?, val legacyKey: ByteArray)

    private suspend fun passwordRoot(metaId: Long): UniversalWalletRoot? = try {
        accountRepository.universalWalletRoot(metaId)?.takeIf(UniversalWalletRoot::hasPassword)
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        // A wallet whose secrets cannot be read has nothing to switch here.
        null
    }

    private fun legacySlots(wallet: MetaAccount, root: UniversalWalletRoot): List<LegacySlot> =
        UniversalWalletSlot.entries.mapNotNull { slot ->
            val (chainId, stored) = wallet.universalWalletSlotAccount(slot) ?: return@mapNotNull null
            LegacySlot(slot, chainId, stored).takeIf { root.holdsLegacy(slot, stored) }
        }

    /** Entries of [root]'s words and password; a re-import with other ones watches nothing old. */
    private fun watchedEntries(metaId: Long, root: UniversalWalletRoot): Map<UniversalWalletSlot, UniversalWalletWatchList.Entry> =
        watchList.entries(metaId).filter { entry ->
            entry.slot.publicKey(root.mnemonic, "").contentEquals(entry.oldKey) &&
                entry.slot.publicKey(root.mnemonic, root.passphrase).contentEquals(entry.newKey)
        }.associateBy { it.slot }

    // Slots that kept the new address while their old one received funds.
    private fun arrivalRows(wallet: MetaAccount, watched: Collection<UniversalWalletWatchList.Entry>): List<Row> =
        watched.filter { entry ->
            entry.arrival?.kind == Arrival.Kind.OLD_ADDRESS &&
                wallet.universalWalletSlotAccount(entry.slot)?.second?.contentEquals(entry.newKey) == true
        }.map { entry ->
            Row(entry.slot, entry.slot.address(entry.oldKey), entry.slot.address(entry.newKey), arrival = entry.arrival)
        }

    private suspend fun probe(
        slot: UniversalWalletSlot,
        mnemonic: String,
        passphrase: String,
        publicKey: ByteArray
    ): UniversalWalletFundsProbe.Reading = try {
        fundsProbe.read(slot, mnemonic, passphrase, publicKey)
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        UniversalWalletFundsProbe.Reading(UniversalWalletFundsProbe.Funds.UNKNOWN)
    }

    /** Moves the slot from [fromKey] to [toKey], derived with [toPassphrase]; both CAS on [fromKey]. */
    private suspend fun move(
        metaId: Long,
        slot: UniversalWalletSlot,
        chainId: String?,
        fromKey: ByteArray,
        toKey: ByteArray,
        root: UniversalWalletRoot,
        toPassphrase: String
    ): Outcome = try {
        val moved = if (slot == UniversalWalletSlot.TON) {
            moveTon(metaId, fromKey, root, toPassphrase)
        } else {
            WalletCrossStoreMutationMutex.instance.withLock {
                metaAccountDao.replaceDerivedChainAccount(
                    metaId = metaId,
                    chainId = checkNotNull(chainId),
                    expectedPublicKey = fromKey,
                    publicKey = toKey,
                    accountId = toKey.clone()
                ) == 1
            }
        }
        if (moved) Outcome.SWITCHED else Outcome.UNKNOWN
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: WalletSecretMutationCoordinatorException) {
        if (failure.reason == WalletMutationFailureReason.CONNECTED_APPS) Outcome.IN_USE else Outcome.UNKNOWN
    } catch (_: Exception) {
        Outcome.UNKNOWN
    }

    private suspend fun moveTon(metaId: Long, fromKey: ByteArray, root: UniversalWalletRoot, toPassphrase: String): Boolean {
        val before = metaAccountDao.getMetaAccount(metaId) ?: return false
        if (before.tonPublicKey?.contentEquals(fromKey) != true) return false
        val derived = TonKeyDerivation.deriveAccount(root.mnemonic, toPassphrase)
        try {
            val after = MetaAccountLocal(
                before.substratePublicKey, before.substrateCryptoType, before.substrateAccountId,
                before.ethereumPublicKey, before.ethereumAddress, derived.publicKey,
                before.name, before.isSelected, before.position, before.isBackedUp,
                before.googleBackupAddress, before.initialized
            ).apply { id = before.id }
            val secrets = TonSecrets(root.mnemonic.encodeToByteArray(), Keypair(derived.publicKey, derived.privateKey))
            coordinator.replaceUniversalTon(before, after, secrets.toHexString())
        } finally {
            derived.privateKey.fill(0)
            derived.chainCode.fill(0)
        }
        metaAccountDao.deleteAccountAssets(metaId, fromKey)
        return true
    }

    private fun dismissedKey(metaId: Long) = "$DISMISSED_KEY_PREFIX$metaId"

    private companion object {
        const val DISMISSED_KEY_PREFIX = "universal_wallet_password_notice.v1."
        const val DISMISSED = "dismissed"
    }
}
