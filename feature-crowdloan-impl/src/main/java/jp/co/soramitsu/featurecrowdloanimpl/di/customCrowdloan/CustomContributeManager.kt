package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan

import android.content.Context
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import kotlinx.coroutines.CoroutineScope

class CustomContributeManager(
    private val factories: Set<CustomContributeFactory>
) {

    fun isCustomFlowSupported(flowType: String): Boolean {
        return relevantFactoryOrNull(flowType) != null
    }

    fun createNewState(
        flowType: String,
        scope: CoroutineScope,
        payload: CustomContributePayload
    ): CustomContributeViewState {
        return relevantFactory(flowType).createViewState(scope, payload)
    }

    fun getSubmitter(flowType: String): CustomContributeSubmitter {
        return relevantFactory(flowType).submitter
    }

    fun createView(flowType: String, context: Context, step: MoonbeamCrowdloanStep): CustomContributeView {
        return relevantFactory(flowType).createView(context, step)
    }

    private fun relevantFactory(flowType: String) = relevantFactoryOrNull(flowType) ?: noFactoryFound(flowType)

    private fun relevantFactoryOrNull(
        flowType: String
    ): CustomContributeFactory? {
        return factories.firstOrNull { it.supports(flowType) }
    }

    private fun noFactoryFound(flowType: String): Nothing = throw NoSuchElementException("Factory for $flowType was not found")
}
