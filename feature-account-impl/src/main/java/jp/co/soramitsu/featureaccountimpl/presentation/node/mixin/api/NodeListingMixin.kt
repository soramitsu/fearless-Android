package jp.co.soramitsu.featureaccountimpl.presentation.node.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface NodeListingMixin {

    fun groupedNodeModelsLiveData(chainId: ChainId): LiveData<List<Any>>
}
