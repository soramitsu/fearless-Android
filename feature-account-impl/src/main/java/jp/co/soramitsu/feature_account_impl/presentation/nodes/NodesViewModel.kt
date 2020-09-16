package jp.co.soramitsu.feature_account_impl.presentation.nodes

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class NodesViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeListingMixin: NodeListingMixin
): BaseViewModel(), NodeListingMixin by nodeListingMixin {

    fun editClicked() {
        // TODO
    }

    fun backClicked() {
        router.back()
    }

    fun infoClicked(networkModel: NetworkModel) {
        // TODO
    }

    fun selectNetworkClicked(networkModel: NetworkModel) {
        // TODO
    }
}