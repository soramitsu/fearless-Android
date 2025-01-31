package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.tonconnect.api.model.DappConfig

@Stable
data class DiscoverDappState(
    val dapps: List<DappConfig>,
    val multiToggleButtonState: MultiToggleButtonState<DappListType>,
) {
    companion object {
        val default = DiscoverDappState(
            multiToggleButtonState = MultiToggleButtonState(DappListType.Discover, DappListType.entries),
            dapps = emptyList()
        )
    }
}
