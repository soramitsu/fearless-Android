package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import kotlinx.coroutines.flow.Flow

interface StakingInteractor {

    suspend fun getSelectedNetworkType(): Node.NetworkType

    fun selectedAccountFlow(): Flow<StakingAccount>
}