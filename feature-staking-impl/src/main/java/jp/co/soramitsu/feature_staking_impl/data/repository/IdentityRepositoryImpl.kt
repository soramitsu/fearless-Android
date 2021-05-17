package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.feature_staking_api.domain.api.AccountAddressMap
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.model.ChildIdentity
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_api.domain.model.SuperOf
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindIdentity
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSuperOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdentityRepositoryImpl(
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    val bulkRetriever: BulkRetriever
) : IdentityRepository {

    override suspend fun getIdentitiesFromIds(accountIdsHex: List<String>) = withContext(Dispatchers.Default) {
        val runtime = runtimeProperty.get()

        val identityModule = runtime.metadata.module("Identity")

        val identityOfStorage = identityModule.storage("IdentityOf")
        val identityOfReturnType = identityOfStorage.type.value!!

        val superOfStorage = identityModule.storage("SuperOf")
        val superOfReturnType = superOfStorage.type.value!!
        val superOfKeys = superOfStorage.accountMapStorageKeys(runtime, accountIdsHex)

        val superOfValues = bulkRetriever.queryKeys(superOfKeys)
            .mapKeys { (fullKey, _) -> fullKey.accountIdFromMapKey() }
            .mapValuesNotNull { (_, value) ->
                value?.let { bindSuperOf(it, runtime, superOfReturnType) }
            }

        val parentIdentityIds = superOfValues.values.map(SuperOf::parentIdHex).distinct()
        val parentIdentityKeys = identityOfStorage.accountMapStorageKeys(runtime, parentIdentityIds)

        val parentIdentities = fetchIdentities(parentIdentityKeys, runtime, identityOfReturnType)

        val childIdentities = superOfValues.mapValues { (_, superOf) ->
            val parentIdentity = parentIdentities[superOf.parentIdHex]

            parentIdentity?.let { ChildIdentity(superOf.childName, it) }
        }

        val leftAccountIds = accountIdsHex.toSet() - childIdentities.keys - parentIdentities.keys
        val leftIdentityKeys = identityOfStorage.accountMapStorageKeys(runtime, leftAccountIds.toList())

        val rootIdentities = fetchIdentities(leftIdentityKeys, runtime, identityOfReturnType)

        rootIdentities + childIdentities + parentIdentities
    }

    override suspend fun getIdentitiesFromAddresses(accountAddresses: List<String>): AccountAddressMap<Identity?> {
        val accountIds = accountAddresses.map(String::toHexAccountId)

        val identitiesByAccountId = getIdentitiesFromIds(accountIds)

        return accountAddresses.associateWith { identitiesByAccountId[it.toHexAccountId()] }
    }

    private suspend fun fetchIdentities(
        keys: List<String>,
        runtime: RuntimeSnapshot,
        returnType: Type<*>
    ): Map<String, Identity?> {
        return bulkRetriever.queryKeys(keys)
            .mapKeys { (fullKey, _) -> fullKey.accountIdFromMapKey() }
            .mapValues { (_, value) ->
                value?.let { bindIdentity(it, runtime, returnType) }
            }
    }
}
