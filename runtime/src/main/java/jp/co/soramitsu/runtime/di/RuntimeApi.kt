package jp.co.soramitsu.runtime.di

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.RuntimeUpdater
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory

interface RuntimeApi {

    fun provideExtrinsicBuilderFactory(): ExtrinsicBuilderFactory

    fun provideRuntimeUpdater(): RuntimeUpdater

    fun provideRuntimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun storageCache(): StorageCache
}