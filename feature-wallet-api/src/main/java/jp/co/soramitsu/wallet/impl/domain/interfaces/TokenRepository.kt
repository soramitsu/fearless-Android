package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.Flow

interface TokenRepository {

    /**
     * A historical result in Token.fiatContext captured before the price read. It does not assert
     * that the selection is still current, including after A -> B -> A. Consumers must retain
     * that identity with the value; presenting it as current needs their own context lifecycle.
     */
    suspend fun getToken(chainAsset: Asset): Token

    /**
     * Each delivered fiat selection first emits a token without an admitted quote, then emits
     * matching prices in that captured Token.fiatContext. Missing/unavailable is not known zero.
     * Consumers must preserve currency/context through mapping and any downstream buffering.
     */
    fun observeToken(chainAsset: Asset): Flow<Token>
}
