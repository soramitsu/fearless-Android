package jp.co.soramitsu.featurewalletimpl.presentation.send.recipient.di

import android.content.ContentResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.featurewalletimpl.presentation.send.recipient.QrBitmapDecoder

@InstallIn(SingletonComponent::class)
@Module
class ChooseRecipientModule {

    @Provides
    fun provideQrCodeDecoder(contentResolver: ContentResolver): QrBitmapDecoder {
        return QrBitmapDecoder(contentResolver)
    }
}
