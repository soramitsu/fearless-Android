package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow

interface TokenRepository {

    suspend fun getToken(chainAsset: Chain.Asset): Token

    fun observeToken(chainAsset: Chain.Asset): Flow<Token>
}
