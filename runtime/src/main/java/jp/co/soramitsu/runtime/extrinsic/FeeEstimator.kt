package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigInteger

class FeeEstimator(
    val substrateCalls: SubstrateCalls,
    val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun estimateFee(
        accountAddress: String,
        formExtrinsic: ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        val extrinsicBuilder = extrinsicBuilderFactory.create(accountAddress, keypairProvider = extrinsicBuilderFactory.fakeKeypairProvider())

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        return substrateCalls.getExtrinsicFee(extrinsic)
    }
}
