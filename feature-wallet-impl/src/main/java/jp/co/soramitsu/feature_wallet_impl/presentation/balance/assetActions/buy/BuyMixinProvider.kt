package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.IntegrationPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.Presentation
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.ProviderChooserPayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

// TODO wallet - buy
class BuyMixinProvider(
    private val buyTokenRegistry: BuyTokenRegistry,
) : Presentation {

    override val showProviderChooserEvent = MutableLiveData<Event<ProviderChooserPayload>>()

    override val integrateWithBuyProviderEvent = MutableLiveData<Event<IntegrationPayload>>()

    override fun isBuyEnabled(chainId: ChainId, chainAssetId: Int): Boolean {
        return false
    }

    override fun providerChosen(
        provider: BuyTokenRegistry.Provider<*>,
        chainAsset: Chain.Asset,
    ) {
//        val payload = IntegrationPayload(
//            provider = provider,
//            token = token,
//            address = accountAddress
//        )
//
//        integrateWithBuyProviderEvent.value = Event(payload)
    }

    override fun buyClicked(chainId: ChainId, chainAssetId: Int) {
//        val availableProviders = buyTokenRegistry.availableProviders(chainAsset)
//
//        when {
//            availableProviders.isEmpty() -> throw IllegalArgumentException("No provider found for ${token.displayName}")
//            availableProviders.size == 1 -> providerChosen(availableProviders.first(), token, accountAddress)
//            else -> showProviderChooserEvent.value = Event(ProviderChooserPayload(availableProviders, token, accountAddress))
//        }
    }
}
