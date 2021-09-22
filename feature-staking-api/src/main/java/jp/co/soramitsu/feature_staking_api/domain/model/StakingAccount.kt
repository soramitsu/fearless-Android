package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.core.model.Network

class StakingAccount(
    val address: String,
    val name: String?,
    val network: Network
)
