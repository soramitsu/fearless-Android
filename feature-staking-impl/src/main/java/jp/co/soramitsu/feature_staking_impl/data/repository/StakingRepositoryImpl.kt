package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.SlashingSpan
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindIdentity
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashDeferDuration
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashingSpans
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.activeEraStorageKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingRepositoryImpl(
    val storageCache: StorageCache,
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    val bulkRetriever: BulkRetriever
) : StakingRepository {

    override suspend fun getTotalIssuance(): BigInteger = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val fullKey = runtime.metadata.module("Balances").storage("TotalIssuance").storageKey()

        val scale = storageCache.getEntry(fullKey).content!!

        bindTotalInsurance(scale, runtime)
    }

    override suspend fun getActiveEraIndex(): BigInteger {
        val runtime = getRuntime()

        val fullKey = runtime.metadata.activeEraStorageKey()

        val scale = storageCache.getEntry(fullKey).content!!

        return bindActiveEra(scale, runtime)
    }

    override suspend fun getElectedValidatorsExposure(eraIndex: BigInteger) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.module("Staking").storage("ErasStakers").storageKey(runtime, eraIndex)

        storageCache.getEntries(prefixKey).associate {
            val accountId = extractAccountIdFromFullKey(it.storageKey)

            accountId to bindExposure(it.content!!, runtime)
        }
    }

    override suspend fun getElectedValidatorsPrefs(eraIndex: BigInteger) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.module("Staking").storage("ErasValidatorPrefs").storageKey(runtime, eraIndex)

        storageCache.getEntries(prefixKey).associate {
            val accountId = extractAccountIdFromFullKey(it.storageKey)

            accountId to bindValidatorPrefs(it.content!!, runtime)
        }
    }

    override suspend fun getIdentities(accountIdsHex: List<String>) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val storage = runtime.metadata.module("Identity").storage("IdentityOf")
        val fullKeys = storage.storageKeys(runtime, accountIdsHex)

        val returnType = storage.type.value!!

        bulkRetriever.queryKeys(fullKeys)
            .mapKeys { (fullKey, _) -> extractAccountIdFromFullKey(fullKey) }
            .mapValues { (_, value) ->
                value?.let { bindIdentity(it, runtime, returnType) }
            }
    }

    override suspend fun getSlashes(accountIdsHex: List<String>) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val storage = runtime.metadata.module("Identity").storage("IdentityOf")
        val fullKeys = storage.storageKeys(runtime, accountIdsHex)

        val activeEraIndex = getActiveEraIndex()

        val returnType = storage.type.value!!

        val slashDeferDurationConstant = runtime.metadata.module("Staking").constant("SlashDeferDuration")
        val slashDeferDuration = bindSlashDeferDuration(slashDeferDurationConstant, runtime)

        bulkRetriever.queryKeys(fullKeys)
            .mapKeys { (fullKey, _) -> extractAccountIdFromFullKey(fullKey) }
            .mapValues { (_, value) ->
                val span = value?.let { bindSlashingSpans(it, runtime, returnType) }

                isSlashed(span, activeEraIndex, slashDeferDuration)
            }
    }

    private fun isSlashed(
        span: SlashingSpan?,
        activeEraIndex: BigInteger,
        slashDeferDuration: BigInteger
    ) = span != null && activeEraIndex - span.lastNonZeroSlash < slashDeferDuration

    private fun StorageEntry.storageKeys(runtime: RuntimeSnapshot, accountIdsHex: List<String>): List<String> {
        return accountIdsHex.map { storageKey(runtime, it.fromHex()) }
    }

    private suspend fun getRuntime() = runtimeProperty.get()

    private fun extractAccountIdFromFullKey(fullKey: String) = fullKey.fromHex().takeLast(32).toByteArray().toHexString()
}