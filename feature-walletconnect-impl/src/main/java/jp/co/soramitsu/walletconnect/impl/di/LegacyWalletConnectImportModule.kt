package jp.co.soramitsu.walletconnect.impl.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport

/**
 * Declares [LegacyWalletConnectImport] optional: an importer binds it with `@Binds` in its own
 * module, and without one `WalletConnectStarter` uses [LegacyWalletConnectImport.NotAttempted].
 */
@InstallIn(SingletonComponent::class)
@Module
interface LegacyWalletConnectImportModule {
    @BindsOptionalOf
    fun legacyWalletConnectImport(): LegacyWalletConnectImport
}
