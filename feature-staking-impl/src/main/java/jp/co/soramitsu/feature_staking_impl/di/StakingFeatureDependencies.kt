package jp.co.soramitsu.feature_staking_impl.di

import com.google.gson.Gson
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.calls.RpcCalls
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

interface StakingFeatureDependencies {

    fun tokenUseCase(): TokenUseCase

    fun computationalCache(): ComputationalCache

    fun feeLoaderMixin(): FeeLoaderMixin.Presentation

    fun runtimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun accountRepository(): AccountRepository

    fun storageCache(): StorageCache

    fun bulkRetriever(): BulkRetriever

    fun addressIconGenerator(): AddressIconGenerator

    fun appLinksProvider(): AppLinksProvider

    fun walletRepository(): WalletRepository

    fun tokenRepository(): TokenRepository

    fun resourceManager(): ResourceManager

    fun extrinsicBuilderFactory(): ExtrinsicBuilderFactory

    fun substrateCalls(): RpcCalls

    fun externalAccountActions(): ExternalAccountActions.Presentation

    fun socketService(): SocketService

    fun assetCache(): AssetCache

    fun accountStakingDao(): AccountStakingDao

    fun accountUpdateScope(): AccountUpdateScope

    fun stakingRewardsDao(): StakingRewardDao

    fun stakingTotalRewardsDao(): StakingTotalRewardDao

    fun networkApiCreator(): NetworkApiCreator

    fun httpExceptionHandler(): HttpExceptionHandler

    fun walletConstants(): WalletConstants

    fun gson(): Gson

    fun addressxDisplayUseCase(): AddressDisplayUseCase

    fun feeEstimator(): FeeEstimator

    fun extrinsicService(): ExtrinsicService

    fun validationExecutor(): ValidationExecutor

    @Named(REMOTE_STORAGE_SOURCE)
    fun remoteStorageSource(): StorageDataSource

    @Named(LOCAL_STORAGE_SOURCE)
    fun localStorageSource(): StorageDataSource
}
