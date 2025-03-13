package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigInteger

interface ValidationChecksProvider {
    suspend fun provide(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationAddress: String,
        fee: BigInteger
    ): Map<TransferValidationResult, Boolean>
}

