package jp.co.soramitsu.runtime.di

import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Named

interface RuntimeApi {

    fun provideExtrinsicBuilderFactory(): ExtrinsicBuilderFactory

    fun externalRequirementFlow(): MutableStateFlow<ChainConnection.ExternalRequirement>

    fun storageCache(): StorageCache

    @Named(REMOTE_STORAGE_SOURCE)
    fun remoteStorageSource(): StorageDataSource

    @Named(LOCAL_STORAGE_SOURCE)
    fun localStorageSource(): StorageDataSource

    fun chainSyncService(): ChainSyncService

    fun chainStateRepository(): ChainStateRepository

    fun chainRegistry(): ChainRegistry
}
