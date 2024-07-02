package jp.co.soramitsu.liquiditypools.impl.domain

import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository

class PoolsInteractorImpl(
    private val polkaswapRepository: PolkaswapRepository,
) : PoolsInteractor {

    override suspend fun getBasicPools(): List<BasicPoolData> {
        return polkaswapRepository.getBasicPools()
    }
}
