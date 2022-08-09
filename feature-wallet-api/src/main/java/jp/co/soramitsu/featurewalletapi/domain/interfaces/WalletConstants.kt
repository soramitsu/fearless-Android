package jp.co.soramitsu.featurewalletapi.domain.interfaces

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigInteger

interface WalletConstants {

    suspend fun existentialDeposit(chainId: ChainId): BigInteger

    suspend fun tip(chainId: ChainId): BigInteger?
}
