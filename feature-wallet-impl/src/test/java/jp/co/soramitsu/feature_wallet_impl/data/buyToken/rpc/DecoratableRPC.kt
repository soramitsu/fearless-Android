package jp.co.soramitsu.feature_wallet_impl.data.buyToken.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.Decoratable

class DecoratableRPC(
    private val socketService: SocketService,
) : Decoratable() {

    fun <R : DecoratableRPCModule> decorate(moduleName: String, creator: DecoratableRPCModule.() -> R): R = decorateInternal(moduleName) {
        creator(DecoratableRPCModuleImpl(moduleName, socketService))
    }

    private class DecoratableRPCModuleImpl(
        private val moduleName: String,
        private val socketService: SocketService,
    ) : DecoratableRPCModule {

        override val decorator: DecoratableRPCModule.Decorator = object : DecoratableRPCModule.Decorator, Decoratable() {

            override fun <R> call0(callName: String, binder: (Any?) -> R): RpcCall0<R> {
                return decorateInternal(callName) {
                    RpcCall0(moduleName, callName, socketService, binder)
                }
            }

            override fun <A : Any, R> call1(callName: String, binder: (Any?) -> R): RpcCall1<A, R> {
                return decorateInternal(callName) {
                    RpcCall1(moduleName, callName, socketService, binder)
                }
            }

            override fun <A : Any, R> callList(callName: String, binder: (Any?) -> R): RpcCallList<A, R> {
                return decorateInternal(callName) {
                    RpcCallList(moduleName, callName, socketService, binder)
                }
            }

            override fun <R> subscription0(callName: String, binder: (SubscriptionChange) -> R): RpcSubscription0<R> {
                return decorateInternal(callName) {
                    RpcSubscription0(moduleName, callName, socketService, binder)
                }
            }

            override fun <A : Any, R> subscription1(callName: String, binder: (SubscriptionChange) -> R): RpcSubscription1<A, R> {
                return decorateInternal(callName) {
                    RpcSubscription1(moduleName, callName, socketService, binder)
                }
            }

            override fun <A : Any, R> subscriptionList(callName: String, binder: (SubscriptionChange) -> R): RpcSubscriptionList<A, R> {
                return decorateInternal(callName) {
                    RpcSubscriptionList(moduleName, callName, socketService, binder)
                }
            }
        }
    }
}
