package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.map

class StakingScenario(private val state: StakingSharedState) {

    val viewModel = state.assetWithChain.map {
        when (it.asset.staking) {
            Chain.Asset.StakingType.PARACHAIN -> {
                StakingParachainScenarioViewModel()
            }
            Chain.Asset.StakingType.RELAYCHAIN -> {
                StakingRelaychainScenarioViewModel()
            }
            else -> error("")
        }
    }
}


