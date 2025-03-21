package jp.co.soramitsu.account.impl.domain.account.details

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.domain.model.hasTon
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain.From
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

class AccountDetailsInteractor(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val assetNotNeedAccountUseCase: AssetNotNeedAccountUseCase
) {

    suspend fun getMetaAccount(metaId: Long): MetaAccount {
        return accountRepository.getMetaAccount(metaId)
    }

    fun lightMetaAccountFlow(metaId: Long) =
        accountRepository.lightMetaAccountFlow(metaId)

    suspend fun hasReplacedAccounts(metaId: Long, type: WalletEcosystem): Boolean {
        val wallet = getMetaAccount(metaId)
        return wallet.chainAccounts.values.mapNotNull { it.chain }.any {
            if (it.isEthereumChain) {
                type == WalletEcosystem.Ethereum
            } else {
                type == WalletEcosystem.Substrate
            }
        }
    }

    fun getChainProjectionsFlow(metaId: Long): Flow<GroupedList<From, AccountInChain>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            flowOf { chainRegistry.getChains() }.map { it.sortedWith(chainSort()) },
            assetNotNeedAccountUseCase.getAssetsMarkedNotNeedFlow(metaId)
        ) { metaAccount, chains, assetsMarkedNotNeed ->
            chains.flatMap { chain ->
                chain.assets.map { chainAsset ->
                    val markedNotNeed = assetsMarkedNotNeed.contains(
                        AssetKey(metaId, chain.id, emptyAccountIdValue, chainAsset.id)
                    )
                    createAccountInChain(metaAccount, chain, markedNotNeed)
                }
            }
                .distinctBy { it.from to it.chain.id to it.projection?.address }
                .groupBy(AccountInChain::from)
                .toSortedMap(compareBy { it.name })
        }
    }

    fun getChainProjectionsFlow(metaId: Long, type: WalletEcosystem): Flow<GroupedList<From, AccountInChain>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            flowOf { chainRegistry.getChains() }//.map { it.sortedWith(chainSort()) },
        ) { metaAccount, chains ->
            chains.filter { chain ->
                chain.ecosystem == Ecosystem.Ton && type == WalletEcosystem.Ton
                        || chain.ecosystem == Ecosystem.Ethereum && type == WalletEcosystem.Ethereum
                        || chain.ecosystem == Ecosystem.EthereumBased && type == WalletEcosystem.Ethereum
                        || chain.ecosystem == Ecosystem.Substrate && type == WalletEcosystem.Substrate
            }.map { chain ->
                createAccountInChain(metaAccount, chain, false)
            }.filter {
                it.hasAccount
            }.groupBy(AccountInChain::from)
                .toSortedMap(compareBy { it.name })
        }
    }

    fun getChainAccountsSummaryFlow(metaId: Long): Flow<List<Pair<WalletEcosystem, Int>>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            flowOf { chainRegistry.getChains() }
        ) { metaAccount, chains ->
            metaAccount to chains.filter { chain ->
                when (chain.ecosystem) {
                    Ecosystem.Substrate -> metaAccount.hasSubstrate
                    Ecosystem.EthereumBased,
                    Ecosystem.Ethereum -> metaAccount.hasEthereum

                    Ecosystem.Ton -> metaAccount.hasTon
                } || metaAccount.hasChainAccount(chain.id)
            }.groupBy { chain ->
                when (chain.ecosystem) {
                    Ecosystem.Substrate -> WalletEcosystem.Substrate
                    Ecosystem.EthereumBased,
                    Ecosystem.Ethereum -> WalletEcosystem.Ethereum

                    Ecosystem.Ton -> WalletEcosystem.Ton
                }
            }
        }.mapNotNull { (metaAccount, grouped) ->
            if (metaAccount.hasEthereum || metaAccount.hasSubstrate) {
                return@mapNotNull listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum).map {
                    it to grouped[it].orEmpty().size
                }
            }
            if (metaAccount.hasTon) {
                return@mapNotNull listOf(WalletEcosystem.Ton to grouped[WalletEcosystem.Ton].orEmpty().size)
            }
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun hasChainsWithNoAccount() = accountRepository.selectedMetaAccountFlow()
        .flatMapLatest { metaAccount ->
            combine(
                flowOf { chainRegistry.getChains() }.map { chains ->
                    chains.filter { chain ->
                        if (metaAccount.supportedEcosystems().contains(WalletEcosystem.Ton)) {
                            chain.ecosystem == Ecosystem.Ton
                        } else {
                            true
                        }
                    }
                }.map { it.sortedWith(chainSort()) },
                assetNotNeedAccountUseCase.getAssetsMarkedNotNeedFlow(metaAccount.id)
            ) { chains, assetsMarkedNotNeed ->
                chains.any { chain ->
                    chain.assets.any { chainAsset ->
                        val markedNotNeed = assetsMarkedNotNeed.contains(
                            AssetKey(metaAccount.id, chain.id, emptyAccountIdValue, chainAsset.id)
                        )
                        val hasAccount = !chain.isEthereumBased || metaAccount.ethereumPublicKey != null || metaAccount.hasChainAccount(chain.id)
                        hasAccount.not() && markedNotNeed.not()
                    }
                }
            }
        }

    private fun createAccountInChain(metaAccount: MetaAccount, chain: Chain, markedNotNeed: Boolean): AccountInChain {
        val address = metaAccount.address(chain)
        val accountId = metaAccount.accountId(chain)

        val projection = when {
            address == null || accountId == null -> null
            else -> AccountInChain.Projection(address, accountId)
        }
        val hasAccount = !chain.isEthereumBased || metaAccount.ethereumPublicKey != null || metaAccount.hasChainAccount(chain.id)
        return AccountInChain(
            chain = chain,
            projection = projection,
            from = when {
                metaAccount.hasChainAccount(chain.id) -> From.CHAIN_ACCOUNT
                !hasAccount && !markedNotNeed -> From.ACCOUNT_WO_ADDRESS
                else -> From.META_ACCOUNT
            },
            name = null,
            hasAccount = hasAccount,
            markedAsNotNeed = markedNotNeed
        )
    }

    suspend fun getChainAccountSecret(metaId: Long, chainId: ChainId): EncodableStruct<ChainAccountSecrets>? {
        return accountRepository.getChainAccountSecrets(metaId, chainId)
    }

    private fun chainSort() = compareBy<Chain> { it.id.defaultChainSort() }
        .thenBy { it.name }
}
