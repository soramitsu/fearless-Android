package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.Flow

interface TokenRepository {

    suspend fun getToken(networkType: Node.NetworkType): Token?

    suspend fun getToken(tokenType: Token.Type): Token?

    fun observeToken(networkType: Node.NetworkType): Flow<Token>
}