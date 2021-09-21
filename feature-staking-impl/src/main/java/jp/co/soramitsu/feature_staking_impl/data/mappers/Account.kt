package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount

fun mapAccountToStakingAccount(account: Account) = with(account) {
    StakingAccount(address, name, network)
}

fun mapAccountToStakingAccount(metaAccount: MetaAccount) = with(metaAccount) {
    StakingAccount(address, name, cryptoType, network)
}
