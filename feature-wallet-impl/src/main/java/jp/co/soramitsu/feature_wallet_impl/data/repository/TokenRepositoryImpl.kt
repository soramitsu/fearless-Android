package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.tokenTypeLocalFromNetworkType
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTokenLocalToToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TokenRepositoryImpl(
    private val tokenDao: TokenDao
) : TokenRepository {

    override suspend fun getToken(networkType: Node.NetworkType): Token? {
        val tokenType = Token.Type.fromNetworkType(networkType)

        return getToken(tokenType)
    }

    override suspend fun getToken(tokenType: Token.Type): Token? = withContext(Dispatchers.Default) {
        val tokenTypeLocal = mapTokenTypeToTokenTypeLocal(tokenType)

        val tokenLocal = tokenDao.getToken(tokenTypeLocal) ?: TokenLocal.createEmpty(tokenTypeLocal)

        mapTokenLocalToToken(tokenLocal)
    }

    override fun observeToken(networkType: Node.NetworkType): Flow<Token> {
        return tokenDao.observeToken(tokenTypeLocalFromNetworkType(networkType))
            .map(::mapTokenLocalToToken)
    }
}
