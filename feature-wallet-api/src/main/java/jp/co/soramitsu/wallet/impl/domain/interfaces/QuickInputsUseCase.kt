package jp.co.soramitsu.wallet.impl.domain.interfaces

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface QuickInputsUseCase {

    suspend fun calculateStakingQuickInputs(
        chainId: ChainId,
        assetId: String,
        calculateAvailableAmount: suspend () -> BigDecimal,
        calculateFee: suspend (amount: BigInteger) -> BigInteger
    ): Map<Double, BigDecimal>

    suspend fun calculateTransfersQuickInputs(
        chainId: ChainId,
        assetId: String
    ): Map<Double, BigDecimal>

    suspend fun calculateXcmTransfersQuickInputs(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetId: String
    ): Map<Double, BigDecimal>

    suspend fun calculatePolkaswapQuickInputs(
        assetIdFrom: String,
        assetIdTo: String
    ): Map<Double, BigDecimal>
}