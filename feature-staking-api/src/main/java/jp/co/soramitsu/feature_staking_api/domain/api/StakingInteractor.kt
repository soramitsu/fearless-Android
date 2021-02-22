package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.core.model.Node

interface StakingInteractor {

    suspend fun getSelectedNetworkType(): Node.NetworkType
}