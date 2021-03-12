package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount

fun mapAccountToStakingAccount(account: Account) = with(account) {
    StakingAccount(address, name, cryptoType, network)
}

fun mapAccountToWalletAccount(account: Account) = with(account) {
    WalletAccount(address, name, cryptoType, network)
}
