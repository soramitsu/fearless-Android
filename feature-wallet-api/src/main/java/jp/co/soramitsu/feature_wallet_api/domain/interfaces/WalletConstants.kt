package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigInteger

interface WalletConstants {

    suspend fun existentialDeposit(chainId: ChainId): BigInteger
}
