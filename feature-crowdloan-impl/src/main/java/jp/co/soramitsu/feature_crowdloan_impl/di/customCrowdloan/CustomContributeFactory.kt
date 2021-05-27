package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan

import android.content.Context
import android.view.View
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import javax.inject.Provider

interface CustomContributeFactory {

    val parachainId: ParaId
    val networkType: Node.NetworkType

    val viewStateProvider: Provider<out CustomContributeViewState>
    
    fun createView(context: Context): View

    val submitter: CustomContributeSubmitter
}

fun CustomContributeFactory.supports(otherParaId: ParaId, otherNetworkType: Node.NetworkType) : Boolean {
    return parachainId == otherParaId && otherNetworkType == networkType
}
