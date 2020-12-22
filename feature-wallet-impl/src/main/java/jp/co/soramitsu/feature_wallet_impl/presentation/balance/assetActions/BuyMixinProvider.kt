package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

class BuyMixinProvider(
    private val buyTokenRegistry: BuyTokenRegistry
) : BuyMixin.Presentation {
    private var tokenSource: LiveData<Token.Type>? = null

    override val integrateWithBuyProviderEvent = MutableLiveData<Event<BuyMixin.Payload>>()

    private val bestProviderLiveData by lazy {
        tokenSource!!.map(buyTokenRegistry::findBestProvider)
    }

    override val buyEnabledLiveData by lazy {
        bestProviderLiveData.map { it != null }
    }

    override fun startBuyProcess(address: String) {
        if (tokenSource == null) {
            throw IllegalArgumentException("No token supplied")
        }

        val token = tokenSource!!.value!!
        val bestProvider = bestProviderLiveData.value ?: throw IllegalArgumentException("No provider found for ${tokenSource!!.value}")

        val payload = BuyMixin.Payload(
            provider = bestProvider,
            token = token,
            address = address
        )

        integrateWithBuyProviderEvent.value = Event(payload)
    }

    override fun supplyTokenSource(source: LiveData<Token.Type>) {
        tokenSource = source
    }

    override fun supplyTokenSource(source: Token.Type) {
        tokenSource = MutableLiveData(source)
    }
}