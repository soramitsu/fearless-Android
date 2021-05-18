package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigInteger

class FeeEstimator(
    private val substrateCalls: SubstrateCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun estimateFee(
        accountAddress: String,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        val extrinsicBuilder = extrinsicBuilderFactory.createWithFakeKeyPair(accountAddress)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        return substrateCalls.getExtrinsicFee(extrinsic)
    }
}
