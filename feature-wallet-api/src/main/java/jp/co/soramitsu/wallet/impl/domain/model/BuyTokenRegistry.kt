package jp.co.soramitsu.wallet.impl.domain.model

import androidx.annotation.DrawableRes
import jp.co.soramitsu.core.models.Asset as CoreAsset

class BuyTokenRegistry(val availableProviders: List<Provider<*>>) {

    fun availableProviders(chainAsset: CoreAsset) = availableProviders.filter { it.isTokenSupported(chainAsset) }

    interface Provider<I : Integrator<*>> {
        class UnsupportedTokenException : Exception()

        val name: String

        @get:DrawableRes
        val icon: Int

        fun isTokenSupported(chainAsset: CoreAsset) = name.lowercase() in chainAsset.priceProviders.orEmpty()

        fun createIntegrator(chainAsset: CoreAsset, address: String): I
    }

    interface Integrator<T> {
        fun integrate(using: T)
    }
}
