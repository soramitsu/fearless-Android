package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder

class ExtrinsicService(
    val substrateCalls: SubstrateCalls,
    val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun submitExtrinsic(
        accountAddress: String,
        formExtrinsic: ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val extrinsicBuilder = extrinsicBuilderFactory.create(accountAddress)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        substrateCalls.submitExtrinsic(extrinsic)
    }
}
