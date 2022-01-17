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
        val chains = chainRegistry.currentChains.first()

        chains.map { chain ->
            val address = metaAccount.address(chain)
            val accountId = metaAccount.accountId(chain)

            val projection = if (address != null && accountId != null) {
                AccountInChain.Projection(address, accountId)
            } else {
                null
            }

            AccountInChain(
                chain = chain,
                projection = projection,
                from = if (metaAccount.hasChainAccount(chain.id)) From.CHAIN_ACCOUNT else From.META_ACCOUNT
            )
        }.groupBy(AccountInChain::from)
    }

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<MetaAccountSecrets>? {
        return accountRepository.getMetaAccountSecrets(metaId)
    }
}
