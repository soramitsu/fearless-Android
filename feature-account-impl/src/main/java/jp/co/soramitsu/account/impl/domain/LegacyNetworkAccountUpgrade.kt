package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe
import jp.co.soramitsu.account.api.domain.UniversalWalletRoot
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.universalWalletRoot
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.withLock

/**
 * Adds supported network accounts from the wallet's existing validated root.
 * Chain keys are public-only. Missing TON root material is added by the durable
 * mutation coordinator. Existing roots, ids, ordering and chain accounts are
 * never replaced, deleted or marked backed up.
 *
 * A password wallet's universal accounts derive with its BIP39 passphrase. A
 * missing slot of an imported password wallet first probes the account the
 * words alone open: funded or unknown keeps that one, so no address that holds
 * funds disappears; confirmed empty takes the password-protected one. Wallets
 * created from freshly generated words take the password without a probe.
 */
class LegacyNetworkAccountUpgrade(
    private val accountRepository: AccountRepository,
    private val metaAccountDao: MetaAccountDao,
    private val mutationCoordinator: WalletSecretMutationCoordinator,
    private val fundsProbe: UniversalWalletFundsProbe = UniversalWalletFundsProbe { _, _, _ ->
        UniversalWalletFundsProbe.Funds.UNKNOWN
    },
    private val freshRoots: UniversalWalletFreshRoots? = null,
    private val availableChainIds: suspend () -> Set<String>
) {
    /** Retry for a newly imported wallet or newly available network without a restart. */
    fun observeWallets(scope: CoroutineScope, chainIds: Flow<Set<String>>): Job = combine(
        accountRepository.allMetaAccountsFlow().map { accounts -> accounts.map { it.id }.toSet() }.distinctUntilChanged(),
        chainIds.distinctUntilChanged()
    ) { _, _ -> Unit }
        .onEach { upgrade() }
        .launchIn(scope)

    /** Best-effort and restartable; an unavailable addition cannot block old wallets. */
    suspend fun upgrade() {
        try {
            val availableIds = availableChainIds()
            val walletIds = accountRepository.allMetaAccounts().map { it.id }
            for (walletId in walletIds) {
                try {
                    // The coordinator owns its mutex and rechecks the complete
                    // before-image; do not nest it under the chain-update lock.
                    try {
                        addMissingTonRoot(walletId)
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        // Retry next startup; other networks can still be added.
                    }
                    addMissingChainAccounts(walletId, availableIds)
                    forgetFreshRootWhenComplete(walletId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    // One unreadable wallet must not prevent another wallet's
                    // upgrade. Runtime signing retains its existing safeguards.
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Catalog/database availability is not an onboarding requirement.
        }
    }

    private class PendingSlots(
        val wallet: MetaAccount,
        val root: UniversalWalletRoot,
        val targets: List<Pair<UniversalWalletSlot, String>>
    )

    private suspend fun addMissingChainAccounts(walletId: Long, availableIds: Set<String>) {
        // Serialize the root read, derivation and public insertion against wallet
        // deletion and root-secret replacement. A network probe never holds the lock.
        val toProbe = WalletCrossStoreMutationMutex.instance.withLock {
            val pending = pendingSlots(walletId, availableIds) ?: return
            if (!pending.root.hasPassword || freshRoots?.isCreated(pending.wallet.substratePublicKey) == true) {
                insert(pending) { pending.root.passphrase }
                return
            }
            pending
        }
        val probed = probeLegacy(toProbe.root, toProbe.targets.map { it.first })
        WalletCrossStoreMutationMutex.instance.withLock {
            val pending = pendingSlots(walletId, availableIds) ?: return
            insert(pending) { slot ->
                val decision = probed[slot]
                // A probe of other words (the root changed meanwhile) decides nothing.
                if (decision != null && decision.first.contentEquals(slot.publicKey(pending.root.mnemonic, "")) &&
                    decision.second == UniversalWalletFundsProbe.Funds.EMPTY
                ) {
                    pending.root.passphrase
                } else {
                    ""
                }
            }
        }
    }

    // Keep this priority identical to the production BTC, Solana and Iroha
    // transfer services. Never invent a phrase for seed/key-only or watch-only
    // legacy wallets.
    private suspend fun pendingSlots(walletId: Long, availableIds: Set<String>): PendingSlots? {
        val wallet = accountRepository.getMetaAccount(walletId)
        val existingAliases = wallet.chainAccounts.keys.map { it.lowercase() }.toSet()
        val targets = UniversalWalletSlot.chainRowSlots
            .filter { slot -> slot.chainIds.none { it in existingAliases } }
            .mapNotNull { slot -> slot.chainIds.firstOrNull(availableIds::contains)?.let { slot to it } }
        if (targets.isEmpty()) return null
        val root = accountRepository.universalWalletRoot(walletId) ?: return null
        return PendingSlots(wallet, root, targets)
    }

    private suspend fun insert(pending: PendingSlots, passphraseFor: (UniversalWalletSlot) -> String) {
        for ((slot, chainId) in pending.targets) {
            try {
                val publicKey = slot.publicKey(pending.root.mnemonic, passphraseFor(slot))
                val bitcoin = slot == UniversalWalletSlot.BITCOIN_MAINNET || slot == UniversalWalletSlot.BITCOIN_TESTNET
                metaAccountDao.insertDerivedChainAccountsIfAbsent(
                    listOf(ChainAccountLocal(
                        metaId = pending.wallet.id,
                        chainId = chainId,
                        publicKey = publicKey,
                        accountId = publicKey.clone(),
                        cryptoType = if (bitcoin) CryptoType.ECDSA else CryptoType.ED25519,
                        name = pending.wallet.name,
                        initialized = false
                    ))
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // No completion marker: retry missing networks
                // on next startup. Other additions can proceed.
            }
        }
    }

    /** The no-password public key of each slot and whether it holds anything, probed in parallel. */
    private suspend fun probeLegacy(
        root: UniversalWalletRoot,
        slots: List<UniversalWalletSlot>
    ): Map<UniversalWalletSlot, Pair<ByteArray, UniversalWalletFundsProbe.Funds>> = coroutineScope {
        slots.map { slot ->
            async {
                val legacy = slot.publicKey(root.mnemonic, "")
                val funds = try {
                    fundsProbe.probe(slot, root.mnemonic, legacy)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    UniversalWalletFundsProbe.Funds.UNKNOWN
                }
                slot to (legacy to funds)
            }
        }.awaitAll().toMap()
    }

    private suspend fun addMissingTonRoot(walletId: Long) {
        val wallet = accountRepository.getMetaAccount(walletId)
        if (wallet.tonPublicKey != null || wallet.chainAccounts.keys.any(UniversalWalletSlot.TON::matches)) return
        if (metaAccountDao.getMetaAccount(walletId)?.tonPublicKey != null) return
        // Only a Substrate or Ethereum phrase is a universal root; a TON-only wallet has its own key.
        accountRepository.getSubstrateSecrets(walletId)?.get(SubstrateSecrets.Entropy)
            ?: accountRepository.getEthereumSecrets(walletId)?.get(EthereumSecrets.Entropy)
            ?: return
        val root = accountRepository.universalWalletRoot(walletId) ?: return
        val passphrase = when {
            !root.hasPassword -> ""
            freshRoots?.isCreated(wallet.substratePublicKey) == true -> root.passphrase
            else -> {
                val legacy = UniversalWalletSlot.TON.publicKey(root.mnemonic, "")
                val funds = try {
                    fundsProbe.probe(UniversalWalletSlot.TON, root.mnemonic, legacy)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    UniversalWalletFundsProbe.Funds.UNKNOWN
                }
                if (funds == UniversalWalletFundsProbe.Funds.EMPTY) root.passphrase else ""
            }
        }
        // Read the before-image after any probe; the coordinator rejects a stale one.
        val before = metaAccountDao.getMetaAccount(walletId) ?: return
        if (before.tonPublicKey != null) return
        val derived = TonKeyDerivation.deriveAccount(root.mnemonic, passphrase)
        try {
            val after = MetaAccountLocal(
                before.substratePublicKey, before.substrateCryptoType, before.substrateAccountId,
                before.ethereumPublicKey, before.ethereumAddress, derived.publicKey,
                before.name, before.isSelected, before.position, before.isBackedUp,
                before.googleBackupAddress, before.initialized
            ).apply { id = before.id }
            val secrets = TonSecrets(root.mnemonic.encodeToByteArray(), Keypair(derived.publicKey, derived.privateKey))
            mutationCoordinator.addTon(before, after, secrets.toHexString())
        } finally {
            derived.privateKey.fill(0)
            derived.chainCode.fill(0)
        }
    }

    private suspend fun forgetFreshRootWhenComplete(walletId: Long) {
        val freshRoots = freshRoots ?: return
        val wallet = accountRepository.getMetaAccount(walletId)
        val substratePublicKey = wallet.substratePublicKey ?: return
        if (!freshRoots.isCreated(substratePublicKey)) return
        val aliases = wallet.chainAccounts.keys.map { it.lowercase() }.toSet()
        val complete = wallet.tonPublicKey != null &&
            UniversalWalletSlot.chainRowSlots.all { slot -> slot.chainIds.any { it in aliases } }
        if (complete) freshRoots.forget(substratePublicKey)
    }
}
