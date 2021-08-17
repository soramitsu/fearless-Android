package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.network.runtime.calls.RpcCalls
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder

class ExtrinsicService(
    private val rpcCalls: RpcCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun submitExtrinsic(
        accountAddress: String,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val extrinsicBuilder = extrinsicBuilderFactory.create(accountAddress)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        rpcCalls.submitExtrinsic(extrinsic)
    }
}
