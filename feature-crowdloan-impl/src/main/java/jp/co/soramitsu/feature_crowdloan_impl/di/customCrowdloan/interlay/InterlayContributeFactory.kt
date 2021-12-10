package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.interlay

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay.InterlayContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay.InterlayContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay.InterlayContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import kotlinx.coroutines.CoroutineScope

class InterlayContributeFactory(
    override val submitter: InterlayContributeSubmitter,
    private val interactor: InterlayContributeInteractor,
    private val resourceManager: ResourceManager,
) : CustomContributeFactory {

    override val flowType = "interlay"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload) =
        InterlayContributeViewState(interactor, payload, resourceManager)

    override fun createView(context: Context, step: MoonbeamCrowdloanStep?): CustomContributeView = InterlayContributeView(context)
}
