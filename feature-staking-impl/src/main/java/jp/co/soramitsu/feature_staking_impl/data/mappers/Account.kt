package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.address
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun mapAccountToStakingAccount(account: Account) = with(account) {
    StakingAccount(address, name)
}

fun mapAccountToStakingAccount(chain: Chain, metaAccount: MetaAccount) = metaAccount.address(chain)?.let { address ->
    StakingAccount(
        address = address,
        name = metaAccount.name,
    )
}
