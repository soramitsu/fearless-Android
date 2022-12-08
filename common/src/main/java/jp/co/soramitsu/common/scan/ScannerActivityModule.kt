package jp.co.soramitsu.common.scan

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrBitmapDecoder

@InstallIn(SingletonComponent::class)
@Module
class ScannerActivityModule {
    @Provides
    fun provideScannerViewModel(
        resourceManager: ResourceManager,
        qrBitmapDecoder: QrBitmapDecoder
    ): ScannerViewModel = ScannerViewModel(resourceManager, qrBitmapDecoder)
}
