package jp.co.soramitsu.wallet.impl.domain.model

import androidx.annotation.DrawableRes
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.util.Locale

class BuyTokenRegistry(val availableProviders: List<Provider<*>>) {

    fun availableProviders(chainAsset: Chain.Asset) = availableProviders.filter { it.isTokenSupported(chainAsset) }

    interface Provider<I : Integrator<*>> {
        class UnsupportedTokenException : Exception()

        val name: String

        @get:DrawableRes
        val icon: Int

        fun isTokenSupported(chainAsset: Chain.Asset) = name.toLowerCase(Locale.ROOT) in chainAsset.priceProviders.orEmpty()

        fun createIntegrator(chainAsset: Chain.Asset, address: String): I
    }

    interface Integrator<T> {
        fun integrate(using: T)
    }
}
