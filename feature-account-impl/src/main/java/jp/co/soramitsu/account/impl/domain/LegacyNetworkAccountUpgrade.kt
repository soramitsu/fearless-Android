package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 */
class LegacyNetworkAccountUpgrade(
    private val accountRepository: AccountRepository,
    private val metaAccountDao: MetaAccountDao,
    private val mutationCoordinator: WalletSecretMutationCoordinator,
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
                    // Serialize the root read, derivation and public insertion
                    // against wallet deletion and root-secret replacement.
                    WalletCrossStoreMutationMutex.instance.withLock {
                        val wallet = accountRepository.getMetaAccount(walletId)
                        val existingAliases = wallet.chainAccounts.keys.map { it.lowercase() }.toSet()
                        val targets = listOf(
                            setOf(UniversalWalletRegistry.bitcoinMainnet.id, UniversalWalletRegistry.bitcoinMainnet.chainId),
                            setOf(UniversalWalletRegistry.bitcoinTestnet.id, UniversalWalletRegistry.bitcoinTestnet.chainId),
                            setOf(UniversalWalletRegistry.solanaMainnet.id, UniversalWalletRegistry.solanaMainnet.chainId),
                            setOf(UniversalWalletRegistry.taira.id, UniversalWalletRegistry.taira.chainId)
                        ).filter { aliases -> aliases.none { it.lowercase() in existingAliases } }
                            .mapNotNull { aliases -> aliases.firstOrNull(availableIds::contains) }
                        if (targets.isEmpty()) return@withLock

                        // Keep this priority identical to the production BTC,
                        // Solana and Iroha transfer services. Never invent a
                        // phrase for seed/key-only or watch-only legacy wallets.
                        val mnemonic = accountRepository.getSubstrateSecrets(walletId)
                            ?.get(SubstrateSecrets.Entropy)
                            ?.let { MnemonicCreator.fromEntropy(it.clone()).words }
                            ?: accountRepository.getEthereumSecrets(walletId)
                                ?.get(EthereumSecrets.Entropy)
                                ?.let { MnemonicCreator.fromEntropy(it.clone()).words }
                            ?: accountRepository.getTonSecrets(walletId)
                                ?.get(TonSecrets.Seed)?.decodeToString()
                            ?: return@withLock

                        for (chainId in targets) {
                            try {
                                val bitcoin = chainId in setOf(
                                    UniversalWalletRegistry.bitcoinMainnet.id,
                                    UniversalWalletRegistry.bitcoinMainnet.chainId,
                                    UniversalWalletRegistry.bitcoinTestnet.id,
                                    UniversalWalletRegistry.bitcoinTestnet.chainId
                                )
                                val bitcoinNetwork = if (
                                    chainId in setOf(UniversalWalletRegistry.bitcoinTestnet.id, UniversalWalletRegistry.bitcoinTestnet.chainId)
                                ) {
                                    BitcoinKeyDerivation.Network.Testnet
                                } else {
                                    BitcoinKeyDerivation.Network.Mainnet
                                }
                                val publicKey = when {
                                    bitcoin -> BitcoinKeyDerivation.deriveAccount(mnemonic, network = bitcoinNetwork).let {
                                        it.privateKey.fill(0)
                                        it.chainCode.fill(0)
                                        it.publicKey
                                    }
                                    chainId in setOf(UniversalWalletRegistry.solanaMainnet.id, UniversalWalletRegistry.solanaMainnet.chainId) ->
                                        SolanaKeyDerivation.deriveAccount(mnemonic).let {
                                            it.privateKey.fill(0)
                                            it.chainCode.fill(0)
                                            it.publicKey
                                        }
                                    else -> IrohaKeyDerivation.deriveAccount(mnemonic).let {
                                        it.privateKey.fill(0)
                                        it.chainCode.fill(0)
                                        it.publicKey
                                    }
                                }
                                metaAccountDao.insertDerivedChainAccountsIfAbsent(
                                    listOf(ChainAccountLocal(
                                        metaId = walletId,
                                        chainId = chainId,
                                        publicKey = publicKey,
                                        accountId = publicKey.clone(),
                                        cryptoType = if (bitcoin) CryptoType.ECDSA else CryptoType.ED25519,
                                        name = wallet.name,
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

    private suspend fun addMissingTonRoot(walletId: Long) {
        val wallet = accountRepository.getMetaAccount(walletId)
        val tonAliases = setOf(
            UniversalWalletRegistry.tonMainnetRegistryEntry.id, UniversalWalletRegistry.tonMainnetRegistryEntry.chainId, "-239", "-3"
        )
        if (wallet.tonPublicKey != null || wallet.chainAccounts.keys.any { it.lowercase() in tonAliases }) return
        val before = metaAccountDao.getMetaAccount(walletId) ?: return
        if (before.tonPublicKey != null) return
        val entropy = accountRepository.getSubstrateSecrets(walletId)?.get(SubstrateSecrets.Entropy)
            ?: accountRepository.getEthereumSecrets(walletId)?.get(EthereumSecrets.Entropy)
            ?: return
        val mnemonic = MnemonicCreator.fromEntropy(entropy.clone()).words
        val derived = TonKeyDerivation.deriveAccount(mnemonic)
        try {
            val after = MetaAccountLocal(
                before.substratePublicKey, before.substrateCryptoType, before.substrateAccountId,
                before.ethereumPublicKey, before.ethereumAddress, derived.publicKey,
                before.name, before.isSelected, before.position, before.isBackedUp,
                before.googleBackupAddress, before.initialized
            ).apply { id = before.id }
            val secrets = TonSecrets(mnemonic.encodeToByteArray(), Keypair(derived.publicKey, derived.privateKey))
            mutationCoordinator.addTon(before, after, secrets.toHexString())
        } finally {
            derived.privateKey.fill(0)
            derived.chainCode.fill(0)
        }
    }
}
