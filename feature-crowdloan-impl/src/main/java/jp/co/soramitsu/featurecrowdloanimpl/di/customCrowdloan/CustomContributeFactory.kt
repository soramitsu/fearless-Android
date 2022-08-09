package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan

import android.content.Context
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import kotlinx.coroutines.CoroutineScope

interface CustomContributeFactory {

    val flowType: String

    fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): CustomContributeViewState

    fun createView(context: Context, step: MoonbeamCrowdloanStep? = null): CustomContributeView

    val submitter: CustomContributeSubmitter
}

fun CustomContributeFactory.supports(otherFlowType: String): Boolean {
    return otherFlowType == flowType
}
