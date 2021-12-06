package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.Node.NetworkType.KUSAMA
import jp.co.soramitsu.core.model.Node.NetworkType.POLKADOT
import jp.co.soramitsu.core.model.Node.NetworkType.ROCOCO
import jp.co.soramitsu.core.model.Node.NetworkType.WESTEND
import jp.co.soramitsu.feature_account_impl.R

data class NetworkModel(
    val name: String,
    val networkTypeUI: NetworkTypeUI
) {
    sealed class NetworkTypeUI(val icon: Int, val networkType: Node.NetworkType) {
        object Kusama : NetworkTypeUI(R.drawable.ic_ksm_24, KUSAMA)
        object Polkadot : NetworkTypeUI(R.drawable.ic_polkadot_24, POLKADOT)
        object Westend : NetworkTypeUI(R.drawable.ic_westend_24, WESTEND)
        object Rococo : NetworkTypeUI(R.drawable.ic_polkadot_24, ROCOCO)
    }
}
