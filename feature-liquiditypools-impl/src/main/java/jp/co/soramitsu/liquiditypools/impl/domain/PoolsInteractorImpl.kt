package jp.co.soramitsu.liquiditypools.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.CommonUserPoolData
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.flow.Flow

class PoolsInteractorImpl(
    private val polkaswapRepository: PolkaswapRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val chainRegistry: ChainRegistry,
) : PoolsInteractor {

    override suspend fun getBasicPools(): List<BasicPoolData> {
        return polkaswapRepository.getBasicPools()
    }

    override suspend fun getPoolCacheOfCurAccount(
        tokenFromId: String,
        tokenToId: String
    ): CommonUserPoolData? {
        val wallet = accountRepository.getSelectedMetaAccount()
        val chainId = polkaswapInteractor.polkaswapChainId
        val chain = chainRegistry.getChain(chainId)
        val address = wallet.address(chain)
        return polkaswapRepository.getPoolOfAccount(address, tokenFromId, tokenToId, chainId)
    }

    override suspend fun getUserPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto? {
//        return polkaswapRepository.getPoolOfAccount(address, baseTokenId, tokenId.toHexString(true), polkaswapInteractor.polkaswapChainId)
        return polkaswapRepository.getUserPoolData(address, baseTokenId, tokenId)

    }
}
