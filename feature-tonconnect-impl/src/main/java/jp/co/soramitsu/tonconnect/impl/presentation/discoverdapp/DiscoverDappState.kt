package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.compose.runtime.Stable
import co.jp.soramitsu.tonconnect.model.DappConfig
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState

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
