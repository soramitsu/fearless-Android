package jp.co.soramitsu.feature_staking_impl.domain.scenarios

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios.StakingScenarioRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class StakingParachainScenarioInteractor(
    walletRepository: WalletRepository,
    accountRepository: AccountRepository,
    scenarioRepository: StakingScenarioRepository,
    stakingSharedState: StakingSharedState,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val stakingRepository: StakingRepository
) : StakingScenarioInteractor(walletRepository, accountRepository, scenarioRepository, stakingSharedState, stakingRepository) {

    override suspend fun observeNetworkInfoState(chainId: ChainId): Flow<NetworkInfo> {
        val lockupPeriod = getParachainLockupPeriodInDays(chainId)
        val minimumStakeInPlanks = stakingConstantsRepository.parachainMinimumStaking(chainId)

        return flowOf(
            NetworkInfo.Parachain(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStakeInPlanks
            )
        )
    }

    private suspend fun getParachainLockupPeriodInDays(chainId: ChainId): Int {
        val hoursInRound = hoursInRound[chainId] ?: return 0
        val lockupPeriodInRounds = stakingConstantsRepository.parachainLockupPeriodInRounds(chainId).toInt()
        val lockupPeriodInHours = lockupPeriodInRounds * hoursInRound
        return lockupPeriodInHours.toDuration(DurationUnit.HOURS).toInt(DurationUnit.DAYS)
    }

    private val hoursInRound = mapOf(
        "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d" to 6, // moonbeam
        "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b" to 2, // moonriver
        "91bc6e169807aaa54802737e1c504b2577d4fafedd5a02c10293b1cd60e39527" to 2 // moonbase
    )

    override suspend fun getStakingStateFlow(chain: Chain, chainAsset: Chain.Asset, accountId: AccountId): Flow<StakingState> {
        return stakingRepository.observeParachainState(chain, accountId)
    }
}
