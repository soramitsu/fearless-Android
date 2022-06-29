package jp.co.soramitsu.feature_staking_impl.domain.staking.unbond

import java.math.BigInteger
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.chill
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.unbond
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnbondInteractor(
    private val extrinsicService: ExtrinsicService,
    private val stakingRepository: StakingRelayChainScenarioRepository
) {

    suspend fun estimateFee(
        stashState: StakingState.Stash,
        currentBondedBalance: BigInteger,
        amount: BigInteger
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            extrinsicService.estimateFee(stashState.chain) {
                constructUnbondExtrinsic(stashState, currentBondedBalance, amount)
            }
        }
    }

    suspend fun unbond(
        stashState: StakingState.Stash,
        currentBondedBalance: BigInteger,
        amount: BigInteger
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(stashState.chain, stashState.controllerId) {
                constructUnbondExtrinsic(stashState, currentBondedBalance, amount)
            }
        }
    }

    private suspend fun ExtrinsicBuilder.constructUnbondExtrinsic(
        stashState: StakingState.Stash,
        currentBondedBalance: BigInteger,
        unbondAmount: BigInteger
    ) {
        // see https://github.com/paritytech/substrate/blob/master/frame/staking/src/lib.rs#L1614
        if (
            // if account is nominating
            stashState is StakingState.Stash.Nominator &&
            // and resulting bonded balance is less than min bond
            currentBondedBalance - unbondAmount < stakingRepository.minimumNominatorBond(stashState.chain.id)
        ) {
            chill()
        }

        unbond(unbondAmount)
    }

    // unbondings are always going from the oldest to newest so last in the list will be the newest one
    fun newestUnbondingAmount(unbondings: List<Unbonding>) = unbondings.last().amount

    fun allUnbondingsAmount(unbondings: List<Unbonding>): BigInteger = unbondings.sumByBigInteger(Unbonding::amount)
}
