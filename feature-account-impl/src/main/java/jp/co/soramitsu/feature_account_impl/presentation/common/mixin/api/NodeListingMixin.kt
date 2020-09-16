package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.feature_account_impl.presentation.nodes.model.NodeModel

interface NodeListingMixin {

    val nodeListingDisposable: CompositeDisposable

    val groupedNodeModelsLiveData: LiveData<List<Any>>

    val selectedNodeLiveData: MutableLiveData<NodeModel>
}