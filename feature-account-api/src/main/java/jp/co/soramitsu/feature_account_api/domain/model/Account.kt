package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.domain.model.CryptoType
import jp.co.soramitsu.domain.model.Network

data class Account(
    val address: String,
    val name: String?,
    val publicKey: String,
    val cryptoType: CryptoType,
    val position: Int,
    val network: Network
)