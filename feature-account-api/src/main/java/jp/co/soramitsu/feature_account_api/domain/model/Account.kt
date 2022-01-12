package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Network
import jp.co.soramitsu.fearless_utils.extensions.fromHex

data class Account(
    val address: String,
    val name: String?,
    val accountIdHex: String,
    val cryptoType: CryptoType, // TODO make optional
    val position: Int,
    val network: Network, // TODO remove when account management will be rewritten,
) {

    val accountId = accountIdHex.fromHex()
}
