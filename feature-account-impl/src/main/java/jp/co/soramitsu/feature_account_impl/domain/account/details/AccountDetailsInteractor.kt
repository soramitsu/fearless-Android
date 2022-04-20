package jp.co.soramitsu.feature_account_impl.domain.account.details

import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.core_db.dao.emptyAccountIdValue
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_account_api.domain.model.address
import jp.co.soramitsu.feature_account_api.domain.model.hasChainAccount
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain.From
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isPolkadotOrKusama
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AccountDetailsInteractor(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val assetNotNeedAccountUseCase: AssetNotNeedAccountUseCase,
) {

    suspend fun getMetaAccount(metaId: Long): MetaAccount {
        return accountRepository.getMetaAccount(metaId)
    }

    suspend fun updateName(metaId: Long, newName: String) {
        accountRepository.updateMetaAccountName(metaId, newName)
    }

    fun getChainProjectionsFlow(metaId: Long): Flow<GroupedList<From, AccountInChain>> {
        return combine(
            flowOf { getMetaAccount(metaId) },
            chainRegistry.currentChains.map { it.sortedWith(chainSort()) },
            assetNotNeedAccountUseCase.getAssetsMarkedNotNeedFlow(metaId)
        ) { metaAccount, chains, assetsMarkedNotNeed ->
            chains.map { chain ->
                val markedNotNeed = assetsMarkedNotNeed.contains(
                    AssetKey(metaId, chain.id, emptyAccountIdValue, chain.utilityAsset.symbol)
                )
                createAccountInChain(metaAccount, chain, markedNotNeed)
            }
                .groupBy(AccountInChain::from)
                .toSortedMap(compareBy { it.name })
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

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<MetaAccountSecrets>? {
        return accountRepository.getMetaAccountSecrets(metaId)
    }

    suspend fun getChainAccountSecret(metaId: Long, chainId: ChainId): EncodableStruct<ChainAccountSecrets>? {
        return accountRepository.getChainAccountSecrets(metaId, chainId)
    }

    private fun chainSort() = compareByDescending<Chain> { it.id.isPolkadotOrKusama() }
        .thenBy { it.name }
}
