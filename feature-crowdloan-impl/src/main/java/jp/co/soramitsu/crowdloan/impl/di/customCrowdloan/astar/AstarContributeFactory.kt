package jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.astar

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar.AstarContributeSubmitter
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar.AstarContributeView
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar.AstarContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import kotlinx.coroutines.CoroutineScope

class AstarContributeFactory(
    override val submitter: AstarContributeSubmitter,
    private val interactor: AstarContributeInteractor,
    private val resourceManager: ResourceManager
) : CustomContributeFactory {

    override val flowType = "astar"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): AstarContributeViewState =
        AstarContributeViewState(interactor, payload, resourceManager)

    override fun createView(context: Context, step: MoonbeamCrowdloanStep?): CustomContributeView = AstarContributeView(context)
}
