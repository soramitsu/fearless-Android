package jp.co.soramitsu.feature_staking_impl.scenarios

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

interface StakingScenarioInteractor {

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo>

    suspend fun getStakingStateFlow(): Flow<StakingState>
    suspend fun getMinimumStake(chainId: ChainId): BigInteger
    suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean

    fun currentUnbondingsFlow(): Flow<List<Unbonding>>
    suspend fun getSelectedAccountStakingState(): StakingState
    suspend fun getStakingBalanceFlow(collatorId: AccountId? = null): Flow<StakingBalanceModel>
    fun overrideRedeemActionTitle(): Int?
}
