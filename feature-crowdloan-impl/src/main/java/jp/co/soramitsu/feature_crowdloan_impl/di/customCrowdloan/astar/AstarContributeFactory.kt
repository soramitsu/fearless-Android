package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.astar

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import kotlinx.coroutines.CoroutineScope

class AstarContributeFactory(
    override val submitter: AstarContributeSubmitter,
    private val interactor: AstarContributeInteractor,
    private val resourceManager: ResourceManager,
) : CustomContributeFactory {

    override val flowType = "Astar"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): AstarContributeViewState {
        return AstarContributeViewState(interactor, payload, resourceManager)
    }

    override fun createView(context: Context) = AstarContributeView(context)
}
