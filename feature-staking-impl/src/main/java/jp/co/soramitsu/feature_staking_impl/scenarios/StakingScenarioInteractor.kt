package jp.co.soramitsu.feature_staking_impl.scenarios

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface StakingScenarioInteractor {

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo>

    fun getStakingStateFlow(): Flow<StakingState>
    suspend fun getMinimumStake(chainId: ChainId): BigInteger
    suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean

    suspend fun currentUnbondingsFlow(): Flow<List<Unbonding>>
    suspend fun getSelectedAccountStakingState(): StakingState

    suspend fun getStakingBalanceFlow(collatorId: AccountId? = null): Flow<StakingBalanceModel>
    fun overrideRedeemActionTitle(): Int?
    suspend fun accountIsNotController(controllerAddress: String): Boolean
    suspend fun ledger(): StakingLedger?
    suspend fun checkAccountRequiredValidation(accountAddress: String?): Boolean
    suspend fun maxStakersPerBlockProducer(): Int
    suspend fun unstakingPeriod(): Int
    // era for relaychain
    // round for parachain
    suspend fun stakePeriodInHours(): Int
    suspend fun getRewardDestination(accountStakingState: StakingState): RewardDestination
}
