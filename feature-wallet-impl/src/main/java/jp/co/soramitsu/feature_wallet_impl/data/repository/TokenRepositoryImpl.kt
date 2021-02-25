package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTokenLocalToToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TokenRepositoryImpl(
    private val tokenDao: TokenDao,
) : TokenRepository {

    override fun getToken(networkType: Node.NetworkType): Token? {
        val tokenType = Token.Type.fromNetworkType(networkType)

        return getToken(tokenType)
    }

    override fun getToken(tokenType: Token.Type): Token? = withContext(Dispatchers.Default) {
        val tokenLocal = tokenDao.getToken(tokenType)

        mapTokenLocalToToken(tokenLocal)
    }

    override fun observeToken(networkType: Node.NetworkType): Flow<Token> {
        TODO("Not yet implemented")
    }
}