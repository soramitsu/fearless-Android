package jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.data.buyToken.ExternalProvider
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry

interface BuyMixin {
    class IntegrationPayload(
        val provider: BuyTokenRegistry.Provider<*>,
        val chainAsset: Asset,
        val address: String
    )

    class ProviderChooserPayload(
        val providers: List<BuyTokenRegistry.Provider<*>>,
        val chainAsset: Asset,
        val accountAddress: String
    )

    val showProviderChooserEvent: LiveData<Event<ProviderChooserPayload>>

    val integrateWithBuyProviderEvent: LiveData<Event<IntegrationPayload>>

    fun providerChosen(
        provider: BuyTokenRegistry.Provider<*>,
        chainAsset: Asset,
        accountAddress: String
    )

    interface Presentation : BuyMixin {

        override val showProviderChooserEvent: MutableLiveData<Event<ProviderChooserPayload>>

        override val integrateWithBuyProviderEvent: MutableLiveData<Event<IntegrationPayload>>

        fun buyClicked(chainId: ChainId, chainAssetId: String, accountAddress: String)

        fun isBuyEnabled(chainId: ChainId, chainAssetId: String): Boolean
    }
}

fun <V> BaseComposeFragment<V>.setupBuyIntegration(viewModel: V) where V : BaseViewModel, V : BuyMixin {
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
