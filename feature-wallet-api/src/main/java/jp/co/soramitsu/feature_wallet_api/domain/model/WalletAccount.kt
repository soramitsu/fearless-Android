package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.core.model.Network

class WalletAccount(
    val address: String,
    val name: String?,
    val network: Network
)
