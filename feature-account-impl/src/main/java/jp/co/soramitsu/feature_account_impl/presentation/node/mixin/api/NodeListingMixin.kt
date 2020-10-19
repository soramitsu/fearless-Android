package jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel

interface NodeListingMixin {

    val nodeListingDisposable: CompositeDisposable

    val groupedNodeModelsLiveData: LiveData<List<Any>>

    val selectedNodeLiveData: MutableLiveData<NodeModel>
}