package jp.co.soramitsu.feature_staking_impl.data

import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.runtime.state.SingleAssetSharedState.SelectedAsset
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class StakingSharedState(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
) : SingleAssetSharedState {

    override val selectedAsset: Flow<SelectedAsset> = createSelectedAssetFlow()

    private fun createSelectedAssetFlow(): Flow<SelectedAsset> {

        // TODO FLW-1275 - staking tab selector
        return accountRepository.selectedNetworkTypeFlow().map {
            val chain = chainRegistry.getChain(it.chainId)
            val asset = chain.utilityAsset

            SelectedAsset(chain, asset)
        }
            .inBackground()
            .shareIn(GlobalScope, SharingStarted.Lazily, replay = 1)
    }
}
