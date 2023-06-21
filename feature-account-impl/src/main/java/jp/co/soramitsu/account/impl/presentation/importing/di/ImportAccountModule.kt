package jp.co.soramitsu.account.impl.presentation.importing.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.account.impl.presentation.importing.FileReader
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.BuildConfig
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class ImportAccountModule {

    @Provides
    fun provideFileReader(context: Context) = FileReader(context)

    @Provides
    @Singleton
    fun provideBackupService(context: Context): BackupService {
        return BackupService.create(
            token = BuildConfig.WEB_CLIENT_ID,
            context = context
        )
    }
}
