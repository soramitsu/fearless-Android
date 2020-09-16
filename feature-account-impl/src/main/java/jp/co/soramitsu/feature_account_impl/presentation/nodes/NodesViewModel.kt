package jp.co.soramitsu.feature_account_impl.presentation.nodes

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.nodes.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.nodes.model.NodeModel

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

    fun infoClicked(nodeModel: NodeModel) {
        // TODO
    }

    fun selectNetworkClicked(nodeModel: NodeModel) {
        // TODO
    }
}