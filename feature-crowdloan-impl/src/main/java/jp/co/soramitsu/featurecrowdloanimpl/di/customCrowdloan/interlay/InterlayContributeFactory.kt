package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.interlay

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.interlay.InterlayContributeSubmitter
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.interlay.InterlayContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.interlay.InterlayContributeViewState
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import kotlinx.coroutines.CoroutineScope

class InterlayContributeFactory(
    override val submitter: InterlayContributeSubmitter,
    private val interactor: InterlayContributeInteractor,
    private val resourceManager: ResourceManager
) : CustomContributeFactory {

    override val flowType = "interlay"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload) =
        InterlayContributeViewState(interactor, payload, resourceManager)

    override fun createView(context: Context, step: MoonbeamCrowdloanStep?): CustomContributeView = InterlayContributeView(context)
}
