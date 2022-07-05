package jp.co.soramitsu.feature_staking_impl.scenarios

import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional

interface StakingScenarioInteractor {

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo>

    fun getStakingStateFlow(): Flow<StakingState>
    suspend fun getMinimumStake(chainId: ChainId): BigInteger
    suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean

    suspend fun currentUnbondingsFlow(): Flow<List<Unbonding>>
    suspend fun getSelectedAccountStakingState(): StakingState
    fun selectedAccountStakingStateFlow(): Flow<StakingState>

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
    fun getSetupStakingValidationSystem(): ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure>
    fun getSelectedAccountAddress(): Flow<Optional<AddressModel>>
    fun getCollatorAddress(collatorAddress: String?): Flow<Optional<AddressModel>>
    suspend fun stakeMore(extrinsicBuilder: ExtrinsicBuilder, amountInPlanks: BigInteger, candidate: String? = null): ExtrinsicBuilder
    suspend fun stakeLess(
        extrinsicBuilder: ExtrinsicBuilder,
        amountInPlanks: BigInteger,
        stashState: StakingState,
        currentBondedBalance: BigInteger,
        candidate: String? = null
    )

    suspend fun overrideUnbondHint(): String?
    fun overrideUnbondAvailableLabel(): Int?
    suspend fun getUnstakeAvailableAmount(asset: Asset, collatorId: AccountId?): BigDecimal
    suspend fun checkEnoughToUnbondValidation(payload: UnbondValidationPayload): Boolean
    suspend fun checkCrossExistentialValidation(payload: UnbondValidationPayload): Boolean
}
