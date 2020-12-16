package jp.co.soramitsu.feature_wallet_api.domain.model

class BuyTokenRegistry(private val availableProviders: List<Provider>) {

    fun availableProviders(token: Asset.Token) = availableProviders.filter { it.isTokenSupported(token) }

    interface Provider {
        class UnsupportedTokenException : Exception()

        val supportedTokens: Set<Asset.Token>

        fun isTokenSupported(token: Asset.Token): Boolean = token in supportedTokens

        @Throws(UnsupportedTokenException::class)
        fun createPurchaseLink(token: Asset.Token, address: String): String
    }
}