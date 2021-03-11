package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Network

data class StakingAccount(
    val address: String,
    val name: String?,
    val cryptoType: CryptoType,
    val network: Network
)