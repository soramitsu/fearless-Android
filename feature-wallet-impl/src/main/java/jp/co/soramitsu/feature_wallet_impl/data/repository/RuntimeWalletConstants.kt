package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import java.math.BigInteger

class RuntimeWalletConstants(
    private val chainRegistry: ChainRegistry
) : WalletConstants {

    override suspend fun existentialDeposit(chainId: ChainId): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime)
    }
}
