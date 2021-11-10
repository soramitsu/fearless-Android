package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.acala

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import kotlinx.coroutines.CoroutineScope

class AcalaContributeFactory(
    override val submitter: AcalaContributeSubmitter,
    private val interactor: AcalaContributeInteractor,
    private val resourceManager: ResourceManager,
) : CustomContributeFactory {

    override val flowType = "acala"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): AcalaContributeViewState {
        return AcalaContributeViewState(interactor, payload, resourceManager)
    }

    override fun createView(context: Context, step: Int): CustomContributeView = AcalaContributeView(context)
}
