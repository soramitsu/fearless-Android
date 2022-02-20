package jp.co.soramitsu.feature_account_impl.domain.account.details

import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_account_api.domain.model.address
import jp.co.soramitsu.feature_account_api.domain.model.hasChainAccount
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain.From
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isPolkadotOrKusama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AccountDetailsInteractor(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry
) {

    suspend fun getMetaAccount(metaId: Long): MetaAccount {
        return accountRepository.getMetaAccount(metaId)
    }

    suspend fun updateName(metaId: Long, newName: String) {
        accountRepository.updateMetaAccountName(metaId, newName)
    }

    suspend fun getChainProjections(metaAccount: MetaAccount): GroupedList<From, AccountInChain> = withContext(Dispatchers.Default) {
        val chains = chainRegistry.currentChains.first().sortedWith(chainSort())
        chains.map { it.toAccountInChain(metaAccount) }
            .groupBy(AccountInChain::from)
            .toSortedMap(compareBy { it.name })
    }

    fun Chain.toAccountInChain(metaAccount: MetaAccount): AccountInChain {
        val address = metaAccount.address(this)
        val accountId = metaAccount.accountId(this)

        val projection = when {
            address == null || accountId == null -> null
            else -> AccountInChain.Projection(address, accountId)
        }

        return AccountInChain(
            chain = this,
            projection = projection,
            from = if (metaAccount.hasChainAccount(this.id)) From.CHAIN_ACCOUNT else From.META_ACCOUNT,
            name = null
        )
    }

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<MetaAccountSecrets>? {
        return accountRepository.getMetaAccountSecrets(metaId)
    }

    private fun chainSort() = compareByDescending<Chain> { it.id.isPolkadotOrKusama() }
        .thenBy { it.name }
}
