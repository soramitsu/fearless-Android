package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.bifrost

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost.BifrostContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost.BifrostContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.coroutines.CoroutineScope

class BifrostContributeFactory(
    override val submitter: BifrostContributeSubmitter,
    private val interactor: BifrostContributeInteractor,
    private val resourceManager: ResourceManager,
    private val termsLink: String
) : CustomContributeFactory {

    override val flowType = "Bifrost"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): BifrostContributeViewState {
        return BifrostContributeViewState(interactor, payload, resourceManager, termsLink)
    }

    override fun createView(context: Context) = ReferralContributeView(context)
}
