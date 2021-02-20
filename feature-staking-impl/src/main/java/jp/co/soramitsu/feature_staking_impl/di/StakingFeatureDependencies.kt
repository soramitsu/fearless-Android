package jp.co.soramitsu.feature_staking_impl.di

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface StakingFeatureDependencies {

    fun runtimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun accountRepository(): AccountRepository

    fun storageCache(): StorageCache

    fun defaultPagedKeysRetriever(): BulkRetriever
}