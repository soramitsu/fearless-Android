package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.fromHex

data class Account(
    val address: String,
    val name: String?,
    val accountIdHex: String,
    val cryptoType: CryptoType, // TODO make optional
    val position: Int
) {

    val accountId = accountIdHex.fromHex()
}
