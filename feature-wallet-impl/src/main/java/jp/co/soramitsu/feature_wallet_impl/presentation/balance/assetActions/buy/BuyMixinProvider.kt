package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.IntegrationPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.Presentation
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin.ProviderChooserPayload

class BuyMixinProvider(
    private val buyTokenRegistry: BuyTokenRegistry,
) : Presentation {

    override val showProviderChooserEvent = MutableLiveData<Event<ProviderChooserPayload>>()

    override val integrateWithBuyProviderEvent = MutableLiveData<Event<IntegrationPayload>>()

    override fun isBuyEnabled(token: Token.Type): Boolean = buyTokenRegistry.availableProviders(token).isNotEmpty()

    override fun providerChosen(provider: BuyTokenRegistry.Provider<*>, token: Token.Type, accountAddress: String) {
        val payload = IntegrationPayload(
            provider = provider,
            token = token,
            address = accountAddress
        )

        integrateWithBuyProviderEvent.value = Event(payload)
    }

    override fun buyClicked(token: Token.Type, accountAddress: String) {
        val availableProviders = buyTokenRegistry.availableProviders(token)

        when {
            availableProviders.isEmpty() -> throw IllegalArgumentException("No provider found for ${token.displayName}")
            availableProviders.size == 1 -> providerChosen(availableProviders.first(), token, accountAddress)
            else -> showProviderChooserEvent.value = Event(ProviderChooserPayload(availableProviders, token, accountAddress))
        }
    }
}
