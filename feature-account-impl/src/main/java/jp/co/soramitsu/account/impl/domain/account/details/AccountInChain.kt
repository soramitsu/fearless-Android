package jp.co.soramitsu.account.impl.domain.account.details

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId

class AccountInChain(
    val chain: Chain,
    val projection: Projection?,
    val from: From,
    val name: String?,
    val hasAccount: Boolean,
    val markedAsNotNeed: Boolean
) {

    class Projection(val address: String, val accountId: AccountId)

    enum class From {
        ACCOUNT_WO_ADDRESS, META_ACCOUNT, CHAIN_ACCOUNT
    }
}
