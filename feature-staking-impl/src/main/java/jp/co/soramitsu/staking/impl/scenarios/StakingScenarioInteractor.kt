package jp.co.soramitsu.staking.impl.scenarios

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingLedger
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface StakingScenarioInteractor {

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo>

    val stakingStateFlow: Flow<StakingState>
    suspend fun getMinimumStake(chainAsset: Chain.Asset): BigInteger
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
    suspend fun getSetupStakingValidationSystem(): ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure>
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
        candidate: String? = null,
        chilled: Boolean = true
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
    suspend fun provideBondMoreValidationSystem(): BondMoreValidationSystem
    suspend fun getAvailableForBondMoreBalance(): BigDecimal
}
