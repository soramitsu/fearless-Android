package jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.metadata.Function
import jp.co.soramitsu.fearless_utils.runtime.metadata.Module
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.SubstrateApi

class Function0(
    module: Module,
    function: Function,
    api: SubstrateApi,
) : FunctionBase(
    module, function, api
) {
    operator fun invoke() = createExtrinsic(emptyMap())
}

class Function1<A1>(
    module: Module,
    function: Function,
    api: SubstrateApi,
) : FunctionBase(
    module, function, api
) {
    operator fun invoke(firstArgument: A1): SubmittableExtrinsic {
        require(function.arguments.size == 1)

        return createExtrinsic(mapOf(
            function.arguments.first().name to firstArgument
        ))
    }
}

abstract class FunctionBase(
    private val module: Module,
    protected val function: Function,
    private val api: SubstrateApi,
) {

    protected fun createExtrinsic(vararg values: Any?): SubmittableExtrinsic {
        require(values.size == function.arguments.size)

        val argumentMap = function.arguments.zip(values) { argument, value -> argument.name to value }
            .toMap()

        return createExtrinsic(argumentMap)
    }

    protected fun createExtrinsic(argumentMap: Map<String, Any?>) = SubmittableExtrinsic(GenericCall.Instance(module, function, argumentMap), api)
}
