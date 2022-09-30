package jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.acala

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.acala.AcalaContributeSubmitter
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.acala.AcalaContributeView
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.acala.AcalaContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import kotlinx.coroutines.CoroutineScope

class AcalaContributeFactory(
    override val submitter: AcalaContributeSubmitter,
    private val interactor: AcalaContributeInteractor,
    private val resourceManager: ResourceManager
) : CustomContributeFactory {

    override val flowType = "acala"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): AcalaContributeViewState =
        AcalaContributeViewState(interactor, payload, resourceManager)

    override fun createView(context: Context, step: MoonbeamCrowdloanStep?): CustomContributeView = AcalaContributeView(context)
}
