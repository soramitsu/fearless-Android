package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.activeEraStorageKey
import java.math.BigInteger

class StakingRepositoryImpl(
    val storageCache: StorageCache,
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) : StakingRepository {

    override suspend fun getTotalIssuance(): BigInteger {
        val runtime = getRuntime()

        val fullKey = runtime.metadata.module("Balances").storage("TotalIssuance").storageKey()

        val scale = storageCache.getEntry(fullKey).content!!

        return bindTotalInsurance(scale, runtime)
    }

    override suspend fun getActiveEraIndex(): BigInteger {
        val runtime = getRuntime()

        val fullKey = runtime.metadata.activeEraStorageKey()

        val scale = storageCache.getEntry(fullKey).content!!

        return bindActiveEra(scale, runtime)
    }

    override suspend fun getElectedValidatorsExposure(eraIndex: BigInteger): AccountIdMap<Exposure> {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.module("Staking").storage("ErasStakers").storageKey(runtime, eraIndex)

        return storageCache.getEntries(prefixKey).associate {
            val accountId = extractAccountIdFromFullKey(it.storageKey)

            accountId to bindExposure(it.content!!, runtime)
        }
    }

    override suspend fun getElectedValidatorsPrefs(eraIndex: BigInteger): AccountIdMap<ValidatorPrefs> {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.module("Staking").storage("ErasValidatorPrefs").storageKey(runtime, eraIndex)

        return storageCache.getEntries(prefixKey).associate {
            val accountId = extractAccountIdFromFullKey(it.storageKey)

            accountId to bindValidatorPrefs(it.content!!, runtime)
        }
    }

    private suspend fun getRuntime() = runtimeProperty.get()

    private fun extractAccountIdFromFullKey(fullKey: String) = fullKey.fromHex().takeLast(32).toByteArray().toHexString()
}