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
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationSystem

interface StakingScenarioInteractor {

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo>

    val stakingStateFlow: Flow<StakingState>
    suspend fun getMinimumStake(chainId: ChainId): BigInteger
    suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean

    suspend fun currentUnbondingsFlow(collatorAddress: String?): Flow<List<Unbonding>>
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
    fun getRedeemValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
    fun getBondMoreValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
    fun getUnbondingValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
    fun getRebondValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
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
    suspend fun confirmRevoke(extrinsicBuilder: ExtrinsicBuilder, candidate: String?, stashState: StakingState)

    suspend fun overrideUnbondHint(): String?
    fun overrideUnbondAvailableLabel(): Int?
    suspend fun getUnstakeAvailableAmount(asset: Asset, collatorId: AccountId?): BigDecimal
    fun getRebondAvailableAmount(asset: Asset, amount: BigDecimal): BigDecimal
    suspend fun checkEnoughToUnbondValidation(payload: UnbondValidationPayload): Boolean
    suspend fun checkEnoughToRebondValidation(payload: RebondValidationPayload): Boolean
    suspend fun checkCrossExistentialValidation(payload: UnbondValidationPayload): Boolean
    fun getRebondTypes(): Set<RebondKind>
    suspend fun getRebondingUnbondings(collatorAddress: String?): List<Unbonding>
    fun rebond(extrinsicBuilder: ExtrinsicBuilder, amount: BigInteger, candidate: String?): ExtrinsicBuilder
    fun getUnbondValidationSystem(): UnbondValidationSystem
    fun getRebondValidationSystem(): RebondValidationSystem
    fun provideRedeemValidationSystem(): RedeemValidationSystem
    fun provideBondMoreValidationSystem(): BondMoreValidationSystem
}
