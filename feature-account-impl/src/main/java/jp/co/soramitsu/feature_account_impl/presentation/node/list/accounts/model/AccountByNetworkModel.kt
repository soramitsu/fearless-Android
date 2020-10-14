package jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.model

import jp.co.soramitsu.common.account.AddressModel

data class AccountByNetworkModel(
    val nodeId: Int,
    val accountAddress: String,
    val name: String?,
    val addressModel: AddressModel
)