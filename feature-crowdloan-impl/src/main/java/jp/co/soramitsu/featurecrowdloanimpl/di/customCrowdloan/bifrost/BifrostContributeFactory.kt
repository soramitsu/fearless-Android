package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.bifrost

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.bifrost.BifrostContributeSubmitter
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.bifrost.BifrostContributeViewState
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.coroutines.CoroutineScope

class BifrostContributeFactory(
    override val submitter: BifrostContributeSubmitter,
    private val interactor: BifrostContributeInteractor,
    private val resourceManager: ResourceManager
) : CustomContributeFactory {

    override val flowType = "bifrost"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): BifrostContributeViewState {
        return BifrostContributeViewState(interactor, payload, resourceManager)
    }

    override fun createView(context: Context, step: MoonbeamCrowdloanStep?): CustomContributeView = ReferralContributeView(context)
}
