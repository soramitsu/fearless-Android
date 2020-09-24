package jp.co.soramitsu.feature_account_impl.presentation.node.list

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel

class NodesViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeListingMixin: NodeListingMixin
) : BaseViewModel(), NodeListingMixin by nodeListingMixin {

    fun editClicked() {
        // TODO
    }

    fun backClicked() {
        router.back()
    }

    fun infoClicked(nodeModel: NodeModel) {
        router.openNodeDetails(nodeModel.id)
    }

    fun selectNodeClicked(nodeModel: NodeModel) {
        // TODO
    }

    fun addNodeClicked() {
        router.openAddNode()
    }
}