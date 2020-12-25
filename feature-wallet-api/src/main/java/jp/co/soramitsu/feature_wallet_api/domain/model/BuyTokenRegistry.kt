package jp.co.soramitsu.feature_wallet_api.domain.model

class BuyTokenRegistry(val availableProviders: List<Provider<*>>) {

    fun findBestProvider(type: Token.Type) = availableProviders(type).firstOrNull()

    fun availableProviders(type: Token.Type) = availableProviders.filter { it.isTokenSupported(type) }

    interface Provider<I : Integrator<*>> {
        class UnsupportedTokenException : Exception()

        val supportedTokens: Set<Token.Type>

        fun isTokenSupported(type: Token.Type): Boolean = type in supportedTokens

        fun createIntegrator(type: Token.Type, address: String): I
    }

    interface Integrator<T> {
        fun integrate(using: T)
    }
}