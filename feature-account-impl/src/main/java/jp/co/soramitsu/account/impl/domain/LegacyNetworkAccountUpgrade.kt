package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Adds supported network accounts from the wallet's existing validated root.
 * Only public keys are inserted: transfer signing derives the same keys from
 * the existing encrypted mnemonic. Root secrets, wallet ids, ordering and
 * chain-specific accounts are never replaced, deleted or marked backed up.
 */
class LegacyNetworkAccountUpgrade(
    private val accountRepository: AccountRepository,
    private val metaAccountDao: MetaAccountDao,
    private val availableChainIds: suspend () -> Set<String>
) {
    /** Best-effort and restartable; an unavailable addition cannot block old wallets. */
    suspend fun upgrade() {
        try {
            val availableIds = availableChainIds()
            val walletIds = accountRepository.allMetaAccounts().map { it.id }
            for (walletId in walletIds) {
                try {
                    // Serialize the root read, derivation and public insertion
                    // against wallet deletion and root-secret replacement.
                    WalletCrossStoreMutationMutex.instance.withLock {
                        val wallet = accountRepository.getMetaAccount(walletId)
                        val targets = listOf(
                            setOf(UniversalWalletRegistry.bitcoinMainnet.id, UniversalWalletRegistry.bitcoinMainnet.chainId),
                            setOf(UniversalWalletRegistry.solanaMainnet.id, UniversalWalletRegistry.solanaMainnet.chainId),
                            setOf(UniversalWalletRegistry.taira.id, UniversalWalletRegistry.taira.chainId)
                        ).filter { aliases -> aliases.none(wallet.chainAccounts::containsKey) }
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
                                    UniversalWalletRegistry.bitcoinMainnet.chainId
                                )
                                val publicKey = when {
                                    bitcoin -> BitcoinKeyDerivation.deriveAccount(mnemonic).let {
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
}
