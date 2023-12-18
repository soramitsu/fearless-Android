package co.jp.soramitsu.walletconnect.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.Transfer

interface WalletConnectInteractor {

    suspend fun getChains(): List<Chain>

    suspend fun performTransfer(
        chain: Chain,
        transfer: Transfer,
        privateKey: String
    ): Result<String>
}
