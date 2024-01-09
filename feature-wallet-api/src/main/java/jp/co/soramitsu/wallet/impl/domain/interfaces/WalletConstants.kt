package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigInteger

interface WalletConstants {

    suspend fun existentialDeposit(chainAsset: Asset): BigInteger?

    suspend fun tip(chainId: ChainId): BigInteger?
}
