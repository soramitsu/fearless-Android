package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface BuyMixin {
    class IntegrationPayload(
        val provider: BuyTokenRegistry.Provider<*>,
        val chainAsset: Chain.Asset,
        val address: String,
    )

    class ProviderChooserPayload(
        val providers: List<BuyTokenRegistry.Provider<*>>,
        val chainAsset: Chain.Asset,
    )

    val showProviderChooserEvent: LiveData<Event<ProviderChooserPayload>>

    val integrateWithBuyProviderEvent: LiveData<Event<IntegrationPayload>>

    fun providerChosen(
        provider: BuyTokenRegistry.Provider<*>,
        chainAsset: Chain.Asset
    )

    interface Presentation : BuyMixin {

        override val showProviderChooserEvent: MutableLiveData<Event<ProviderChooserPayload>>

        override val integrateWithBuyProviderEvent: MutableLiveData<Event<IntegrationPayload>>

        fun buyClicked(chainId: ChainId, chainAssetId: Int)

        fun isBuyEnabled(chainId: ChainId, chainAssetId: Int): Boolean
    }
}

fun <V> BaseFragment<V>.setupBuyIntegration(viewModel: V) where V : BaseViewModel, V : BuyMixin {
    viewModel.integrateWithBuyProviderEvent.observeEvent {
        with(it) {
            when (provider) {
                is ExternalProvider -> provider.createIntegrator(it.chainAsset, address).integrate(requireContext())
            }
        }
    }

    viewModel.showProviderChooserEvent.observeEvent { payload ->
        BuyProviderChooserBottomSheet(
            requireContext(), payload.providers,
            onClick = {
                viewModel.providerChosen(it, payload.chainAsset)
            }
        ).show()
    }
}
