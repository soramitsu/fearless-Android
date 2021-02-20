package jp.co.soramitsu.feature_staking_impl.domain.rewards

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.Commission
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.Exposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.activeEraStorageKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class RewardCalculatorFactory(
    private val storageCache: StorageCache,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) {

    suspend fun create(): RewardCalculator = withContext(Dispatchers.Default) {
        val runtime = runtimeProperty.get()

        val activeEraKey = getActiveEraKey(runtime)

        val exposures = getExposures(activeEraKey, runtime)
        val validatorsPrefs = getValidatorPrefs(activeEraKey, runtime)

        val totalIssuance = getTotalIssuance(runtime)

        val validators = exposures.keys.map { accountIdHex ->
            val exposure = exposures[accountIdHex] ?: validatorNotFound(accountIdHex)
            val commission = validatorsPrefs[accountIdHex] ?: validatorNotFound(accountIdHex)

            Validator(
                accountIdHex = accountIdHex,
                totalStake = exposure.total,
                nominatorStakes = exposure.others,
                ownStake = exposure.own,
                commission = commission
            )
        }

        RewardCalculator(
            validators = validators,
            totalIssuance = totalIssuance
        )
    }

    private suspend fun getTotalIssuance(runtime: RuntimeSnapshot): BigInteger {
        val fullKey = runtime.metadata.module("Balances").storage("TotalIssuance").storageKey()

        val scale = storageCache.getEntry(fullKey).content!!

        return bindTotalInsurance(scale, runtime)
    }

    private suspend fun getActiveEraKey(runtime: RuntimeSnapshot): BigInteger {
        val fullKey = runtime.metadata.activeEraStorageKey()

        val scale = storageCache.getEntry(fullKey).content!!

        return bindActiveEra(scale, runtime)
    }

    private suspend fun getExposures(eraKey: BigInteger, runtime: RuntimeSnapshot): Map<String, Exposure> {
        val prefixKey = runtime.metadata.module("Staking").storage("ErasStakers").storageKey(runtime, eraKey)

        return storageCache.getEntries(prefixKey).associate {
            val accountId = extractAccountIdFromFullKey(it.storageKey)

            accountId to bindExposure(it.content!!, runtime)
        }
    }

    private suspend fun getValidatorPrefs(eraKey: BigInteger, runtime: RuntimeSnapshot): Map<String, Commission> {
        val prefixKey = runtime.metadata.module("Staking").storage("ErasValidatorPrefs").storageKey(runtime, eraKey)

        return storageCache.getEntries(prefixKey).associate {
            val accountId = extractAccountIdFromFullKey(it.storageKey)

            accountId to bindValidatorPrefs(it.content!!, runtime)
        }
    }

    private fun validatorNotFound(validatorId: String): Nothing = error("Validator with account id $validatorId was not found")

    private fun extractAccountIdFromFullKey(fullKey: String) = fullKey.fromHex().takeLast(32).toByteArray().toHexString()
}