package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.Flow

interface TokenRepository {

    suspend fun getToken(chainAsset: Asset): Token

    fun observeToken(chainAsset: Asset): Flow<Token>
}
