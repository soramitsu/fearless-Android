package jp.co.soramitsu.feature_wallet_api.domain.model

import androidx.annotation.DrawableRes

class BuyTokenRegistry(val availableProviders: List<Provider<*>>) {

    fun availableProviders(type: Token.Type) = availableProviders.filter { it.isTokenSupported(type) }

    interface Provider<I : Integrator<*>> {
        class UnsupportedTokenException : Exception()

        val supportedTokens: Set<Token.Type>

        val name: String

        @get:DrawableRes
        val icon: Int

        fun isTokenSupported(type: Token.Type): Boolean = type in supportedTokens

        fun createIntegrator(tokenType: Token.Type, address: String): I
    }

    interface Integrator<T> {
        fun integrate(using: T)
    }
}
