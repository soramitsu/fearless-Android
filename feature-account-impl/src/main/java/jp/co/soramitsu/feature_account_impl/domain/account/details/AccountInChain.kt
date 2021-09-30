package jp.co.soramitsu.feature_account_impl.domain.account.details

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class AccountInChain(
    val chain: Chain,
    val projection: Projection?,
    val from: From
) {

    class Projection(val address: String, val accountId: AccountId)

    enum class From {
        META_ACCOUNT, CHAIN_ACCOUNT
    }
}
