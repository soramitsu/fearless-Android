package jp.co.soramitsu.feature_wallet_api.domain.model

class BuyTokenRegistry(private val availableProviders: List<Provider<*>>) {

    fun availableProviders(type: Token.Type) = availableProviders.filter { it.isTokenSupported(type) }

    interface Provider<I : Integrator<*>> {
        class UnsupportedTokenException : Exception()

        val supportedTypes: Set<Token.Type>

        fun isTokenSupported(type: Token.Type): Boolean = type in supportedTypes

        fun createIntegrator(type: Token.Type, address: String): I
    }

    interface Integrator<T> {
        fun integrate(using: T, callback: Callback)

        interface Callback {
            fun buyCompleted()
        }
    }
}