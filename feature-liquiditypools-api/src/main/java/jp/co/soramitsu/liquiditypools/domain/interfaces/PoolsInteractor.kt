package jp.co.soramitsu.liquiditypools.domain.interfaces

import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData

interface PoolsInteractor {
    suspend fun getBasicPools(): List<BasicPoolData>
}
