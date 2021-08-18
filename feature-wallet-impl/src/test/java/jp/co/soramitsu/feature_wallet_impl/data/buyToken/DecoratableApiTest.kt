package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountId
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.metadata.Function
import jp.co.soramitsu.fearless_utils.runtime.metadata.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import java.math.BigInteger

interface SubstrateApi {

    val query: QueryDecoratable

    val tx: TxDecoratable

    val rpc: RpcDecoratable
}

interface RpcDecoratable {

}

fun SubstrateApi(
    runtime: RuntimeSnapshot,
    storage: StorageProvider
) = object : SubstrateApi {
    override val query: QueryDecoratable = QueryDecoratable(storage, runtime)
}

interface DecoratableStorageBuilder {

    fun <R> plain(name: String, binder: (Any?) -> R): PlainStorageEntry<R>

    fun <K, R> single(name: String, binder: (Any?) -> R): SingleMapStorageEntry<K, R>
}

interface DecoratableTxBuilder {

    fun tx0(function: String):
}

class TxDecoratable(
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
)

val module = runtime.metadata.module(moduleName)

val builder = DecoratableStorageBuilderImpl(module)

lazyCreate(builder)



abstract class Decoratable(
) {
    private val initialized = mutableMapOf<String, Any?>()

    inline fun <reified R> decorate(key: String, noinline lazyCreate: () -> R): R {
        val unchecked = decorateUnchecked(key, lazyCreate)

        require(unchecked is R)

        return unchecked
    }

    fun decorateUnchecked(moduleName: String, lazyCreate: () -> Any?): Any? {
        return initialized.getOrPut(moduleName) {
            lazyCreate()
        }
    }
}

class QueryDecoratable(
    private val storage: StorageProvider,
    private val runtime: RuntimeSnapshot,
) {

    private val initializedModules = mutableMapOf<String, Any?>()

    inline fun <reified R> decorate(moduleName: String, noinline lazyCreate: DecoratableStorageBuilder.() -> R): R {
        val unchecked = decorateUnchecked(moduleName, lazyCreate)

        require(unchecked is R)

        return unchecked
    }

    fun <R> decorateUnchecked(moduleName: String, lazyCreate: DecoratableStorageBuilder.() -> R): Any? {
        return initializedModules.getOrPut(moduleName) {
            val module = runtime.metadata.module(moduleName)

            val builder = DecoratableStorageBuilderImpl(module)

            lazyCreate(builder)
        }
    }

    private inner class DecoratableStorageBuilderImpl(
        private val module: Module,
    ) : DecoratableStorageBuilder {

        override fun <R> plain(name: String, binder: (Any?) -> R): PlainStorageEntry<R> {
            return PlainStorageEntry(runtime, storageEntryMetadata(name), storage, binder)
        }

        override fun <K, R> single(name: String, binder: (Any?) -> R): SingleMapStorageEntry<K, R> {
            return SingleMapStorageEntry(runtime, storageEntryMetadata(name), storage, binder)
        }

        private fun storageEntryMetadata(name: String): StorageEntry = module.storage(name)
    }
}

interface StorageProvider {

    suspend fun query(key: String): String?
}

interface ExtrinsicBuilder {

    fun call(module: String, function: String, arguments: Map<String, Any?>): SubmittableExtrinsic
}

class SubmittableExtrinsic(
    val call: GenericCall.Instance
    val api: SubstrateApi
) {

    suspend fun submit(keypair: Keypair) : String {

    }
}

abstract class Function0(
    protected val runtime: RuntimeSnapshot,
    protected val function: Function,
    protected val extrinsicBuilder: ExtrinsicBuilderFactory,
) {

    operator suspend fun invoke() : SubmittableExtrinsic {

    }
}

abstract class StorageEntryBase<R>(
    protected val runtime: RuntimeSnapshot,
    protected val storageEntryMetadata: StorageEntry,
    private val storage: StorageProvider,
    val binder: (Any?) -> R,
) {

    protected suspend fun query(key: String): R? {
        val result = storage.query(key) ?: return null

        val decoded = storageEntryMetadata.type.value!!.fromHexOrIncompatible(result, runtime)

        return binder(decoded)
    }
}

class PlainStorageEntry<R>(
    runtime: RuntimeSnapshot,
    storageEntryMetadata: StorageEntry,
    storage: StorageProvider,
    binder: (Any?) -> R
) : StorageEntryBase<R>(runtime, storageEntryMetadata, storage, binder) {

    suspend operator fun invoke(): R? {
        return query(storageEntryMetadata.storageKey())
    }
}

class SingleMapStorageEntry<K, R>(
    runtime: RuntimeSnapshot,
    storageEntryMetadata: StorageEntry,
    storage: StorageProvider,
    binder: (Any?) -> R
) : StorageEntryBase<R>(runtime, storageEntryMetadata, storage, binder) {

    suspend operator fun invoke(key: K): R? {
        return query(storageEntryMetadata.storageKey(runtime, key))
    }
}

class DecoratableStorage(
    private val storage: Storage
) {

}

interface StakingStorage {



    val historyDepth: PlainStorageEntry<BigInteger>

    val bonded: SingleMapStorageEntry<AccountId, AccountId>
}

val QueryDecoratable.staking: StakingStorage
    get() = decorate("Staking") {
        object : StakingStorage {
            override val historyDepth = plain("HistoryDepth", ::bindNumber)
            override val bonded = single<AccountId, AccountId>("Bonded", ::bindAccountId)
        }
    }

class TestApi {

    suspend fun a(api: SubstrateApi) {
        val bonded = api.query.staking.bonded(key = "0x000001".fromHex())
        val historyDepth = api.query.staking.historyDepth()
    }
}
