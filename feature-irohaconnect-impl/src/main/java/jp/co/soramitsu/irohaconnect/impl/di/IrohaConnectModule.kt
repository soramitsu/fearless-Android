package jp.co.soramitsu.irohaconnect.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.irohaconnect.impl.data.OkHttpIrohaConnectSocketFactory
import jp.co.soramitsu.irohaconnect.impl.data.WalletIrohaConnectAccountProvider
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class IrohaConnectModule {

    private fun irohaConnectDispatcher() = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "IrohaConnect").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /** One process-wide session, so leaving the screen never drops, approves or signs anything. */
    @Provides
    @Singleton
    fun provideIrohaConnectService(
        okHttpClient: OkHttpClient,
        accountRepository: AccountRepository
    ): IrohaConnectService = IrohaConnectService(
            // Its own thread, never the shared IO pool: the app's chain sync can saturate that pool with blocking calls,
            // and a starved event loop stops the keepalive pings Torii needs (seen on a busy emulator: sessions dropped).
            scope = CoroutineScope(SupervisorJob() + irohaConnectDispatcher()),
            socketFactory = OkHttpIrohaConnectSocketFactory(okHttpClient),
            accountProvider = WalletIrohaConnectAccountProvider(accountRepository)
        )
}
