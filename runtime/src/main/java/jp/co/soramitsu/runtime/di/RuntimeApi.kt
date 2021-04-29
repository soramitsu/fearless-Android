package jp.co.soramitsu.runtime.di

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.RuntimeUpdater
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

interface RuntimeApi {

    fun provideExtrinsicBuilderFactory(): ExtrinsicBuilderFactory

    fun provideRuntimeUpdater(): RuntimeUpdater

    fun provideRuntimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun storageCache(): StorageCache

    fun feeEstimator(): FeeEstimator

    fun extrinsicService(): ExtrinsicService

    @Named(REMOTE_STORAGE_SOURCE)
    fun remoteStorageSource(): StorageDataSource

    @Named(LOCAL_STORAGE_SOURCE)
    fun localStorageSource(): StorageDataSource
}
