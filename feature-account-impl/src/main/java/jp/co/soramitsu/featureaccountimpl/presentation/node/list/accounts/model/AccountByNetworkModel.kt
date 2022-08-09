package jp.co.soramitsu.featureaccountimpl.presentation.node.list.accounts.model

import jp.co.soramitsu.common.address.AddressModel

data class AccountByNetworkModel(
    val nodeId: Int,
    val accountAddress: String,
    val name: String?,
    val addressModel: AddressModel
)
