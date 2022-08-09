package jp.co.soramitsu.featurewalletimpl.data.repository

import jp.co.soramitsu.coredb.dao.TokenDao
import jp.co.soramitsu.coredb.model.TokenLocal
import jp.co.soramitsu.featurewalletapi.domain.interfaces.TokenRepository
import jp.co.soramitsu.featurewalletapi.domain.model.Token
import jp.co.soramitsu.featurewalletimpl.data.mappers.mapTokenLocalToToken
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TokenRepositoryImpl(
    private val tokenDao: TokenDao
) : TokenRepository {

    override suspend fun getToken(chainAsset: Chain.Asset): Token = withContext(Dispatchers.Default) {
        val tokenLocal = tokenDao.getToken(chainAsset.symbol) ?: TokenLocal.createEmpty(chainAsset.symbol)

        mapTokenLocalToToken(tokenLocal, chainAsset)
    }

    override fun observeToken(chainAsset: Chain.Asset): Flow<Token> {
        return tokenDao.observeToken(chainAsset.symbol)
            .map {
                mapTokenLocalToToken(it, chainAsset)
            }
    }
}
