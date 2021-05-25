package jp.co.soramitsu.feature_crowdloan_impl.di

import coil.ImageLoader
import com.google.gson.Gson
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

interface CrowdloanFeatureDependencies {

    fun walletConstants(): WalletConstants

    fun feeLoaderMixin(): FeeLoaderMixin.Presentation

    fun storageCache(): StorageCache

    fun imageLoader(): ImageLoader

    fun assetUseCase(): AssetUseCase

    fun runtimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun accountRepository(): AccountRepository

    fun addressIconGenerator(): AddressIconGenerator

    fun appLinksProvider(): AppLinksProvider

    fun walletRepository(): WalletRepository

    fun tokenRepository(): TokenRepository

    fun resourceManager(): ResourceManager

    fun externalAccountActions(): ExternalAccountActions.Presentation

    fun networkApiCreator(): NetworkApiCreator

    fun httpExceptionHandler(): HttpExceptionHandler

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
