package jp.co.soramitsu.feature_account_impl.presentation.node.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class NodeDetailsViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeId: Int
) : BaseViewModel() {

    val nodeLiveData = getNode(nodeId).asLiveData()

    val editEnabled = nodeLiveData.map(::mapNodeEditState)

    private val _updateButtonEnabled = MutableLiveData<Boolean>()
    val updateButtonEnabled: LiveData<Boolean> = _updateButtonEnabled

    fun backClicked() {
        router.back()
    }

    private fun getNode(nodeId: Int): Single<Node> {
        return interactor.getNode(nodeId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun mapNodeEditState(node: Node): Boolean {
        return !node.isDefault
    }

    fun nodeDetailsEdited() {
        _updateButtonEnabled.value = true
    }
}