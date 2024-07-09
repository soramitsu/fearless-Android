package jp.co.soramitsu.liquiditypools.domain.interfaces

import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonUserPoolData

interface PoolsInteractor {
    suspend fun getBasicPools(): List<BasicPoolData>

    suspend fun getPoolCacheOfCurAccount(tokenFromId: String, tokenToId: String): CommonUserPoolData?

    suspend fun getUserPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto?
}
