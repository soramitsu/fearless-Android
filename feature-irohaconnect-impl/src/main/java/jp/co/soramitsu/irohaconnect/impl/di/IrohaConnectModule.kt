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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class IrohaConnectModule {

    /** One process-wide session, so leaving the screen never drops, approves or signs anything. */
    @Provides
    @Singleton
    fun provideIrohaConnectService(
        okHttpClient: OkHttpClient,
        accountRepository: AccountRepository
    ): IrohaConnectService = IrohaConnectService(
            // Every state change runs in the service's single event-loop coroutine; timers only post events.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            socketFactory = OkHttpIrohaConnectSocketFactory(okHttpClient),
            accountProvider = WalletIrohaConnectAccountProvider(accountRepository)
        )
}
