package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan

import android.content.Context
import android.view.View
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState

class CustomContributeManager(
    private val factories: Set<CustomContributeFactory>,
    private val accountRepository: AccountRepository,
) {

    suspend fun isCustomFlowSupported(parachainId: ParaId): Boolean {
        return relevantFactoryOrNull(parachainId) != null
    }

    suspend fun createNewState(parachainId: ParaId): CustomContributeViewState? {
        return relevantFactoryOrNull(parachainId)?.viewStateProvider?.get()
    }

    suspend fun getSubmitter(parachainId: ParaId): CustomContributeSubmitter {
        return relevantFactory(parachainId).submitter
    }

    suspend fun createView(parachainId: ParaId, context: Context): View {
        return relevantFactory(parachainId).createView(context)
    }

    private suspend fun relevantFactory(parachainId: ParaId) = relevantFactoryOrNull(parachainId) ?: noFactoryFound(parachainId)

    private suspend fun relevantFactoryOrNull(
        parachainId: ParaId
    ): CustomContributeFactory? {
        val networkType = accountRepository.currentNetworkType()

        return factories.firstOrNull { it.supports(parachainId, networkType) }
    }

    private fun noFactoryFound(parachainId: ParaId): Nothing = throw NoSuchElementException("Factory for $parachainId was not found")
}
