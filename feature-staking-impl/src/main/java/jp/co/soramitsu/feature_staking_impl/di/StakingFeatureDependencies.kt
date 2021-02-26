package jp.co.soramitsu.feature_staking_impl.di

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface StakingFeatureDependencies {

    fun runtimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun accountRepository(): AccountRepository

    fun storageCache(): StorageCache

    fun bulkRetriever(): BulkRetriever

    fun addressIconGenerator(): AddressIconGenerator

    fun appLinksProvider(): AppLinksProvider

    fun walletRepository(): WalletRepository

    fun tokenRepository(): TokenRepository

    fun resourceManager(): ResourceManager
}