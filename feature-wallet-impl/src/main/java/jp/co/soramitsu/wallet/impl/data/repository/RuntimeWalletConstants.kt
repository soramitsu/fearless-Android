package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import java.math.BigInteger

class RuntimeWalletConstants(
    private val chainRegistry: ChainRegistry
) : WalletConstants {

    override suspend fun existentialDeposit(chainAsset: Asset): BigInteger? {
        val runtime = chainRegistry.getRuntime(chainAsset.chainId)

        return kotlin.runCatching {
            runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime)
        }.getOrNull() ?: chainAsset.existentialDeposit?.toBigIntegerOrNull()
    }

    override suspend fun tip(chainId: ChainId): BigInteger? {
        val runtime = chainRegistry.getRuntime(chainId)

        val constantName = "DefaultTip"

        return kotlin.runCatching {
            runtime.metadata.balances().numberConstant(constantName, runtime)
        }.getOrNull() ?: runtime.overrides?.get(Modules.BALANCES)?.get(constantName)?.toBigInteger()
    }
}
