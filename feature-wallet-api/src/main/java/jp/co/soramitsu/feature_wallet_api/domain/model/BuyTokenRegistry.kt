package jp.co.soramitsu.feature_wallet_api.domain.model

class BuyTokenRegistry(private val availableProviders: List<Provider>) {

    fun availableProviders(type: Token.Type) = availableProviders.filter { it.isTokenSupported(type) }

    interface Provider {
        class UnsupportedTokenException : Exception()

        val supportedTypes: Set<Token.Type>

        fun isTokenSupported(type: Token.Type): Boolean = type in supportedTypes

        @Throws(UnsupportedTokenException::class)
        fun createPurchaseLink(type: Token.Type, address: String): String
    }
}