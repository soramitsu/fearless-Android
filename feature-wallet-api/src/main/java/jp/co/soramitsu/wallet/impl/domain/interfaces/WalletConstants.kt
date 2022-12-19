package jp.co.soramitsu.wallet.impl.domain.interfaces

import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface WalletConstants {

    suspend fun existentialDeposit(chainAsset: Chain.Asset): BigInteger?

    suspend fun tip(chainId: ChainId): BigInteger?
}
