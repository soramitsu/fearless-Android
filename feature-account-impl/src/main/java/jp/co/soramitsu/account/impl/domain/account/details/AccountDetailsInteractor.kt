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
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
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

    fun getChainProjectionsFlow(metaId: Long): Flow<GroupedList<From, AccountInChain>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            chainRegistry.currentChains.map { it.sortedWith(chainSort()) },
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

    fun getChainProjectionsFlow(metaId: Long, type: ImportAccountType): Flow<List<AccountInChain>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            chainRegistry.currentChains.map { it.sortedWith(chainSort()) },
        ) { metaAccount, chains ->
            chains.filter { chain ->
                chain.ecosystem == Ecosystem.Ton && type == ImportAccountType.Ton
                        || chain.ecosystem == Ecosystem.Ethereum && type == ImportAccountType.Ethereum
                        || chain.ecosystem == Ecosystem.EthereumBased && type == ImportAccountType.Ethereum
                        || chain.ecosystem == Ecosystem.Substrate && type == ImportAccountType.Substrate
            }.map { chain ->
                createAccountInChain(metaAccount, chain, false)
            }
        }
    }

    fun getChainAccountsSummaryFlow(metaId: Long): Flow<List<Pair<ImportAccountType, Int>>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            chainRegistry.currentChains,
        ) { metaAccount, chains ->
            val isSubstrateOrEthereumSupportedByAccount = metaAccount.hasEthereum || metaAccount.hasSubstrate
            val isTonSupportedByAccount = metaAccount.hasTon

            chains.filter { chain ->
                chain.ecosystem == Ecosystem.Ton && isTonSupportedByAccount
                        || chain.ecosystem != Ecosystem.Ton && isSubstrateOrEthereumSupportedByAccount
            }.groupBy { chain ->
                when (chain.ecosystem) {
                    Ecosystem.Substrate -> ImportAccountType.Substrate
                    Ecosystem.EthereumBased,
                    Ecosystem.Ethereum -> ImportAccountType.Ethereum

                    Ecosystem.Ton -> ImportAccountType.Ton
                }
            }.map { grouped ->
                val chainsWithAccount = when (grouped.key) {
                    ImportAccountType.Substrate -> metaAccount.hasSubstrate
                    ImportAccountType.Ethereum -> metaAccount.hasEthereum
                    ImportAccountType.Ton -> metaAccount.hasTon
                }

                grouped.key to (grouped.value.size.takeIf { chainsWithAccount } ?: 0)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun hasChainsWithNoAccount() = accountRepository.selectedMetaAccountFlow()
        .flatMapLatest { metaAccount ->
            combine(
                chainRegistry.currentChains.map { chains ->
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
