package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura

import android.content.Context
import android.view.View
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeViewState
import javax.inject.Provider

class KaruraContributeFactory(
    override val viewStateProvider: Provider<KaruraContributeViewState>,
    override val submitter: KaruraContributeSubmitter
) : CustomContributeFactory {

    override val parachainId: ParaId = 3333.toBigInteger()

    override val networkType: Node.NetworkType = Node.NetworkType.ROCOCO

    override fun createView(context: Context): View {
        return KaruraContributeView(context)
    }
}
