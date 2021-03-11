package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

class BuyMixinProvider(
    private val buyTokenRegistry: BuyTokenRegistry
) : BuyMixin.Presentation {
    private var tokenSource: LiveData<Token.Type>? = null

    override val integrateWithBuyProviderEvent = MutableLiveData<Event<BuyMixin.Payload>>()

    override fun buyEnabled(token: Token.Type): Boolean = buyTokenRegistry.findBestProvider(token) != null

    override fun startBuyProcess(token: Token.Type, address: String) {
        val bestProvider = buyTokenRegistry.findBestProvider(token) ?: throw IllegalArgumentException("No provider found for ${tokenSource!!.value}")

        val payload = BuyMixin.Payload(
            provider = bestProvider,
            token = token,
            address = address
        )

        integrateWithBuyProviderEvent.value = Event(payload)
    }
}