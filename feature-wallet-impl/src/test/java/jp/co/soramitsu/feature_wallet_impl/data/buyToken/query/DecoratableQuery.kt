package jp.co.soramitsu.feature_wallet_impl.data.buyToken.query

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.Decoratable
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.SubstrateApi

class DecoratableQuery(
    private val api: SubstrateApi,
    private val runtime: RuntimeSnapshot,
) : Decoratable() {

    fun <R : DecoratableStorage> decorate(moduleName: String, creator: DecoratableStorage.() -> R): R = decorateInternal(moduleName) {
        val module = runtime.metadata.module(moduleName)

        creator(DecoratableStorageImpl(api, runtime, module))
    }

    private class DecoratableStorageImpl(
        private val api: SubstrateApi,
        private val runtime: RuntimeSnapshot,
        private val module: Module,
    ) : DecoratableStorage {

        override val decorator: DecoratableStorage.Decorator = object : DecoratableStorage.Decorator, Decoratable() {

            override fun <R> plain(name: String, binder: (Any?) -> R): PlainStorageEntry<R> {
                return decorateInternal(name) {
                    PlainStorageEntry(runtime, storageEntryMetadata(name), api, binder)
                }
            }

            override fun <K, R> single(name: String, binder: (Any?) -> R): SingleMapStorageEntry<K, R> {
                return decorateInternal(name) {
                    SingleMapStorageEntry(runtime, storageEntryMetadata(name), api, binder)
                }
            }

            private fun storageEntryMetadata(name: String): StorageEntry = module.storage(name)
        }
    }
}
