package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice
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
 */
class UniversalWalletPasswordNoticeService(
    private val accountRepository: AccountRepository,
    private val metaAccountDao: MetaAccountDao,
    private val coordinator: WalletSecretMutationCoordinator,
    private val fundsProbe: UniversalWalletFundsProbe,
    private val preferences: Preferences
) : UniversalWalletPasswordNotice {

    override suspend fun rows(metaId: Long): List<Row> {
        val wallet = accountRepository.getMetaAccount(metaId)
        val root = passwordRoot(metaId) ?: return emptyList()
        return legacySlots(wallet, root).map { slot ->
            Row(slot.slot, slot.slot.address(slot.legacyKey), slot.slot.address(slot.slot.publicKey(root.mnemonic, root.passphrase)))
        }
    }

    override suspend fun switch(metaId: Long): List<Row> {
        val wallet = accountRepository.getMetaAccount(metaId)
        val root = passwordRoot(metaId) ?: return emptyList()
        return legacySlots(wallet, root).map { legacy ->
            val slot = legacy.slot
            val protectedKey = slot.publicKey(root.mnemonic, root.passphrase)
            val funds = try {
                fundsProbe.probe(slot, root.mnemonic, legacy.legacyKey)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                UniversalWalletFundsProbe.Funds.UNKNOWN
            }
            val outcome = when (funds) {
                UniversalWalletFundsProbe.Funds.FUNDED -> Outcome.STILL_FUNDED
                UniversalWalletFundsProbe.Funds.UNKNOWN -> Outcome.UNKNOWN
                UniversalWalletFundsProbe.Funds.EMPTY -> replace(metaId, legacy, protectedKey, root)
            }
            Row(slot, slot.address(legacy.legacyKey), slot.address(protectedKey), outcome)
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

    private suspend fun replace(metaId: Long, legacy: LegacySlot, protectedKey: ByteArray, root: UniversalWalletRoot): Outcome = try {
        val replaced = if (legacy.slot == UniversalWalletSlot.TON) {
            replaceTon(metaId, legacy.legacyKey, root)
        } else {
            WalletCrossStoreMutationMutex.instance.withLock {
                metaAccountDao.replaceDerivedChainAccount(
                    metaId = metaId,
                    chainId = checkNotNull(legacy.chainId),
                    expectedPublicKey = legacy.legacyKey,
                    publicKey = protectedKey,
                    accountId = protectedKey.clone()
                ) == 1
            }
        }
        if (replaced) Outcome.SWITCHED else Outcome.UNKNOWN
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: WalletSecretMutationCoordinatorException) {
        if (failure.reason == WalletMutationFailureReason.CONNECTED_APPS) Outcome.IN_USE else Outcome.UNKNOWN
    } catch (_: Exception) {
        Outcome.UNKNOWN
    }

    private suspend fun replaceTon(metaId: Long, legacyKey: ByteArray, root: UniversalWalletRoot): Boolean {
        val before = metaAccountDao.getMetaAccount(metaId) ?: return false
        if (before.tonPublicKey?.contentEquals(legacyKey) != true) return false
        val derived = TonKeyDerivation.deriveAccount(root.mnemonic, root.passphrase)
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
        metaAccountDao.deleteAccountAssets(metaId, legacyKey)
        return true
    }

    private fun dismissedKey(metaId: Long) = "$DISMISSED_KEY_PREFIX$metaId"

    private companion object {
        const val DISMISSED_KEY_PREFIX = "universal_wallet_password_notice.v1."
        const val DISMISSED = "dismissed"
    }
}
