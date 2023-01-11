package jp.co.soramitsu.polkaswap.api.data

import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface PolkaswapRepository {
    suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger>
}
