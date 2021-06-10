package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura.KaruraContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura.KaruraContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.coroutines.CoroutineScope

class KaruraContributeFactory(
    override val submitter: KaruraContributeSubmitter,
    private val interactor: KaruraContributeInteractor,
    private val resourceManager: ResourceManager
) : CustomContributeFactory {

    override val flowType = "Karura"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): CustomContributeViewState {
        return KaruraContributeViewState(interactor, payload, resourceManager)
    }

    override fun createView(context: Context) = ReferralContributeView(context)
}
