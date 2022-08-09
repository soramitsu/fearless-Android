package jp.co.soramitsu.featurewalletimpl.presentation.balance.assetActions.buy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.featurewalletapi.domain.model.BuyTokenRegistry
import jp.co.soramitsu.featurewalletimpl.data.buyToken.ExternalProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface BuyMixin {
    class IntegrationPayload(
        val provider: BuyTokenRegistry.Provider<*>,
        val chainAsset: Chain.Asset,
        val address: String
    )

    class ProviderChooserPayload(
        val providers: List<BuyTokenRegistry.Provider<*>>,
        val chainAsset: Chain.Asset,
        val accountAddress: String
    )

    val showProviderChooserEvent: LiveData<Event<ProviderChooserPayload>>

    val integrateWithBuyProviderEvent: LiveData<Event<IntegrationPayload>>

    fun providerChosen(
        provider: BuyTokenRegistry.Provider<*>,
        chainAsset: Chain.Asset,
        accountAddress: String
    )

    interface Presentation : BuyMixin {

        override val showProviderChooserEvent: MutableLiveData<Event<ProviderChooserPayload>>

        override val integrateWithBuyProviderEvent: MutableLiveData<Event<IntegrationPayload>>

        fun buyClicked(chainId: ChainId, chainAssetId: String, accountAddress: String)

        fun isBuyEnabled(chainId: ChainId, chainAssetId: String): Boolean
    }
}

fun <V> BaseFragment<V>.setupBuyIntegration(viewModel: V) where V : BaseViewModel, V : BuyMixin {
    viewModel.integrateWithBuyProviderEvent.observeEvent {
        (it.provider as? ExternalProvider)?.createIntegrator(it.chainAsset, it.address)?.integrate(requireContext())
    }

    viewModel.showProviderChooserEvent.observeEvent { payload ->
        BuyProviderChooserBottomSheet(
            requireContext(),
            payload.providers,
            payload.chainAsset,
            onClick = {
                viewModel.providerChosen(it, payload.chainAsset, payload.accountAddress)
            }
        ).show()
    }
}
