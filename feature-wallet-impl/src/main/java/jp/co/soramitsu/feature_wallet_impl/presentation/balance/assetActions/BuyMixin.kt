package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider

interface BuyMixin {
    class Payload(val provider: BuyTokenRegistry.Provider<*>, val token: Token.Type, val address: String)

    val integrateWithBuyProviderEvent: LiveData<Event<Payload>>

    val buyEnabledLiveData: LiveData<Boolean>

    interface Presentation : BuyMixin {
        override val integrateWithBuyProviderEvent: MutableLiveData<Event<Payload>>

        fun startBuyProcess(address: String)

        fun supplyTokenSource(source: LiveData<Token.Type>)

        fun supplyTokenSource(source: Token.Type)
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
}