package jp.co.soramitsu.wallet.impl.domain.interfaces

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface QuickInputsUseCase {

    suspend fun calculateStakingQuickInputs(
        chainId: ChainId,
        assetId: String,
        calculateFee: suspend () -> BigInteger
    ): Map<Double, BigDecimal>

    suspend fun calculateTransfersQuickInputs(
        chainId: ChainId,
        assetId: String
    ): Map<Double, BigDecimal>
}