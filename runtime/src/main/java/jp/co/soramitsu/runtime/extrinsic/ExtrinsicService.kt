package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder

class ExtrinsicService(
    private val substrateCalls: SubstrateCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun submitExtrinsic(
        accountAddress: String,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val extrinsicBuilder = extrinsicBuilderFactory.create(accountAddress)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        substrateCalls.submitExtrinsic(extrinsic)
    }
}
