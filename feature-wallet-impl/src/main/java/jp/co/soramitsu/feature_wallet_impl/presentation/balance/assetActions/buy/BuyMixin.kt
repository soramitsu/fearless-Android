package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider

interface BuyMixin {
    class IntegrationPayload(val provider: BuyTokenRegistry.Provider<*>, val token: Token.Type, val address: String)

    class ProviderChooserPayload(val providers: List<BuyTokenRegistry.Provider<*>>, val token: Token.Type, val address: String)

    val showProviderChooserEvent: LiveData<Event<ProviderChooserPayload>>

    val integrateWithBuyProviderEvent: LiveData<Event<IntegrationPayload>>

    fun providerChosen(provider: BuyTokenRegistry.Provider<*>, token: Token.Type, accountAddress: String)

    interface Presentation : BuyMixin {

        override val showProviderChooserEvent: MutableLiveData<Event<ProviderChooserPayload>>

        override val integrateWithBuyProviderEvent: MutableLiveData<Event<IntegrationPayload>>

        fun buyClicked(token: Token.Type, accountAddress: String)

        fun isBuyEnabled(token: Token.Type): Boolean
    }
}

fun <V> BaseFragment<V>.setupBuyIntegration(viewModel: V) where V : BaseViewModel, V : BuyMixin {
    viewModel.integrateWithBuyProviderEvent.observeEvent {
        with(it) {
            when (provider) {
                is ExternalProvider -> provider.createIntegrator(token, address).integrate(requireContext())
            }
        }
    }

    viewModel.showProviderChooserEvent.observeEvent { payload ->
        BuyProviderChooserBottomSheet(
            requireContext(), payload.providers,
            onClick = {
                viewModel.providerChosen(it, payload.token, payload.address)
            }
        ).show()
    }
}
