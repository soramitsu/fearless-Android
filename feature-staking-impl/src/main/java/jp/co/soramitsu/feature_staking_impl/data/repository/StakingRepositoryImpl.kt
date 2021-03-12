package jp.co.soramitsu.feature_staking_impl.data.repository

import android.util.Log
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Nominations
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.SlashingSpan
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindNominations
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashDeferDuration
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashingSpans
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.activeEraStorageKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingRepositoryImpl(
    val storageCache: StorageCache,
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    val accountStakingDao: AccountStakingDao,
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
            val accountId = it.storageKey.accountIdFromMapKey()

            accountId to bindExposure(it.content!!, runtime)
        }
    }

    override suspend fun getElectedValidatorsPrefs(eraIndex: BigInteger) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.module("Staking").storage("ErasValidatorPrefs").storageKey(runtime, eraIndex)

        storageCache.getEntries(prefixKey).associate {
            val accountId = it.storageKey.accountIdFromMapKey()

            accountId to bindValidatorPrefs(it.content!!, runtime)
        }
    }

    override suspend fun getSlashes(accountIdsHex: List<String>) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val storage = runtime.metadata.module("Staking").storage("SlashingSpans")
        val fullKeys = storage.accountMapStorageKeys(runtime, accountIdsHex)

        val activeEraIndex = getActiveEraIndex()

        val returnType = storage.type.value!!

        val slashDeferDurationConstant = runtime.metadata.module("Staking").constant("SlashDeferDuration")
        val slashDeferDuration = bindSlashDeferDuration(slashDeferDurationConstant, runtime)

        bulkRetriever.queryKeys(fullKeys)
            .mapKeys { (fullKey, _) -> fullKey.accountIdFromMapKey() }
            .mapValues { (_, value) ->
                val span = value?.let { bindSlashingSpans(it, runtime, returnType) }

                isSlashed(span, activeEraIndex, slashDeferDuration)
            }
    }

    override fun stakingStateFlow(accountAddress: String): Flow<StakingState> {
        return accountStakingDao.observeDistinct(accountAddress)
            .flatMapLatest { accountStaking ->
                val accessInfo = accountStaking.stakingAccessInfo

                if (accessInfo == null) {
                    flowOf(StakingState.NonStash(accountStaking.address))
                } else {
                    observeStashState(accessInfo, accountAddress)
                }
            }
    }

    private suspend fun observeStashState(
        accessInfo: AccountStakingLocal.AccessInfo,
        accountAddress: String
    ): Flow<StakingState.Stash> {
        val runtime = runtimeProperty.get()
        val stashId = accessInfo.stashId
        val controllerId = accessInfo.controllerId

        return combine(
            observeAccountNominations(runtime, stashId),
            observeAccountValidatorPrefs(runtime, stashId)
        ) { nominations, prefs ->
            when {
                prefs != null -> StakingState.Stash.Validator(
                    accountAddress, controllerId, stashId, prefs
                )
                nominations != null -> StakingState.Stash.Nominator(
                    accountAddress, controllerId, stashId, nominations
                )

                else -> StakingState.Stash.None(accountAddress, controllerId, stashId)
            }
        }
    }

    private suspend fun observeAccountValidatorPrefs(
        runtime: RuntimeSnapshot,
        stashId: AccountId
    ): Flow<ValidatorPrefs?> {
        val key = runtime.metadata.staking().storage("Validators").storageKey(runtime, stashId)

        return storageCache.observeEntry(key)
            .map { entry ->
                Log.d("RX", "Validator Prefs: ${entry.content}")
                entry.content?.let { bindValidatorPrefs(it, runtime) }
            }
    }

    private suspend fun observeAccountNominations(
        runtime: RuntimeSnapshot,
        stashId: AccountId
    ): Flow<Nominations?> {
        val key = runtime.metadata.staking().storage("Nominators").storageKey(runtime, stashId)

        return storageCache.observeEntry(key)
            .map { entry ->
                Log.d("RX", "Nominations: ${entry.content}")
                entry.content?.let { bindNominations(it, runtime) }
            }
    }

    private fun isSlashed(
        span: SlashingSpan?,
        activeEraIndex: BigInteger,
        slashDeferDuration: BigInteger
    ) = span != null && activeEraIndex - span.lastNonZeroSlash < slashDeferDuration

    private suspend fun getRuntime() = runtimeProperty.get()
}