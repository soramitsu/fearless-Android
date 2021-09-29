package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.feature_account_api.data.mappers.stubNetwork
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.addressIn
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun mapAccountToStakingAccount(account: Account) = with(account) {
    StakingAccount(address, name, network)
}

fun mapAccountToStakingAccount(chain: Chain, metaAccount: MetaAccount) = with(metaAccount) {
    StakingAccount(
        address = addressIn(chain)!!, // TODO may be null in ethereum
        name = name,
        network = stubNetwork(chain.id),
    )
}
