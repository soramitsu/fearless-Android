package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.IntegrationPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.Presentation
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.ProviderChooserPayload
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class BuyMixinProvider(
    private val buyTokenRegistry: BuyTokenRegistry,
    private val chainRegistry: ChainRegistry
) : Presentation {

    override val showProviderChooserEvent = MutableLiveData<Event<ProviderChooserPayload>>()

    override val integrateWithBuyProviderEvent = MutableLiveData<Event<IntegrationPayload>>()

    override fun isBuyEnabled(chainId: ChainId, chainAssetId: Int) =
        when (val asset = chainRegistry.getAsset(chainId, chainAssetId)) {
            null -> false
            else -> buyTokenRegistry.availableProviders(asset).isNotEmpty()
        }

    override fun providerChosen(
        provider: BuyTokenRegistry.Provider<*>,
        chainAsset: Chain.Asset,
        accountAddress: String
    ) {
        val payload = IntegrationPayload(
            provider = provider,
            chainAsset = chainAsset,
            address = accountAddress
        )

        integrateWithBuyProviderEvent.value = Event(payload)
    }

    override fun buyClicked(chainId: ChainId, chainAssetId: Int, accountAddress: String) {
        val asset = chainRegistry.getAsset(chainId, chainAssetId)
        val availableProviders = asset?.let { buyTokenRegistry.availableProviders(it) } ?: emptyList()

        when {
            asset == null -> throw IllegalArgumentException("No asset found with id = $chainAssetId for chain $chainId")
            availableProviders.isEmpty() -> throw IllegalArgumentException("No provider found for ${asset.symbol}")
            availableProviders.size == 1 -> providerChosen(availableProviders.first(), asset, accountAddress)
            else -> showProviderChooserEvent.value = Event(ProviderChooserPayload(availableProviders, asset, accountAddress))
        }
    }
}
