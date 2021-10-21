package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.moonbeam

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.coroutines.CoroutineScope

class MoonbeamContributeFactory(
    override val submitter: MoonbeamContributeSubmitter,
    private val interactor: MoonbeamContributeInteractor,
    private val resourceManager: ResourceManager,
) : CustomContributeFactory {

    override val flowType = "Moonbeam"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): MoonbeamContributeViewState {
        return MoonbeamContributeViewState(interactor, payload, resourceManager)
    }

    override fun createView(context: Context) = ReferralContributeView(context)
}
