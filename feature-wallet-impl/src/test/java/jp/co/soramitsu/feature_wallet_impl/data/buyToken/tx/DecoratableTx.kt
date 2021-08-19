package jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.Decoratable
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.SubstrateApi

class DecoratableTx(
    private val api: SubstrateApi,
    private val runtime: RuntimeSnapshot,
) : Decoratable() {

    fun <R : DecoratableFunctions> decorate(moduleName: String, creator: DecoratableFunctions.() -> R): R = decorateInternal(moduleName) {
        val module = runtime.metadata.module(moduleName)

        creator(DecoratableFunctionsImpl(api, module))
    }

    private class DecoratableFunctionsImpl(
        private val api: SubstrateApi,
        private val module: Module,
    ) : DecoratableFunctions {

        override val decorator: DecoratableFunctions.Decorator = object : DecoratableFunctions.Decorator, Decoratable() {

            override fun function0(name: String): Function0 {
                return decorateInternal(name) {
                    Function0(module, functionMetadata(name), api)
                }
            }

            override fun <A1> function1(name: String): Function1<A1> {
                return decorateInternal(name) {
                    Function1(module, functionMetadata(name), api)
                }
            }

            private fun functionMetadata(name: String) = module.call(name)
        }

    }
}
