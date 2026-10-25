package jp.co.soramitsu.account.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.impl.data.repository.StoredWalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.account.impl.domain.recovery.WalletIdentityVerifier
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder

@Module
@InstallIn(SingletonComponent::class)
object WalletRecoveryModule {
    @Provides
    fun identities(accounts: MetaAccountDao): WalletKeyIdentityReader = StoredWalletKeyIdentityReader(accounts)

    @Provides
    fun verifier(decoder: JsonSeedDecoder) = WalletIdentityVerifier(decoder)

    @Provides
    fun rebind(
        preferences: EncryptedPreferences,
        identities: WalletKeyIdentityReader,
        verifier: WalletIdentityVerifier,
        journal: WalletSecretMutationJournalStore
    ) = WalletSecretRebindService(preferences, identities, verifier, journal)
}
