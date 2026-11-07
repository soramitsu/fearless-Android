package jp.co.soramitsu.walletconnect.impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport
import jp.co.soramitsu.walletconnect.impl.data.legacy.ReownLegacyWalletConnectImport
import javax.inject.Singleton

/**
 * Binds the optional [LegacyWalletConnectImport] that `LegacyWalletConnectImportModule` declares to the
 * Reown import, so the first start after the update carries the old SDK's sessions over.
 */
@InstallIn(SingletonComponent::class)
@Module
class ReownLegacyImportModule {

    @Provides
    @Singleton
    fun provideLegacyWalletConnectImport(context: Context): LegacyWalletConnectImport =
        ReownLegacyWalletConnectImport.create(context)
}
