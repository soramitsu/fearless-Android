package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.karura

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.karura.KaruraContributeSubmitter
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.karura.KaruraContributeViewState
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.coroutines.CoroutineScope

class KaruraContributeFactory(
    override val submitter: KaruraContributeSubmitter,
    private val interactor: KaruraContributeInteractor,
    private val resourceManager: ResourceManager
) : CustomContributeFactory {

    override val flowType = "karura"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): CustomContributeViewState {
        return KaruraContributeViewState(interactor, payload, resourceManager)
    }

    override fun createView(context: Context, step: MoonbeamCrowdloanStep?): CustomContributeView = ReferralContributeView(context)
}
