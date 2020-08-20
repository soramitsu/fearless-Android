package jp.co.soramitsu.feature_account_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.UserDao
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDatasource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDatasourceImpl
import jp.co.soramitsu.feature_account_impl.domain.AccountInteractorImpl

@Module
class AccountFeatureModule {

    @Provides
    @FeatureScope
    fun provideAccountRepository(
        accountDatasource: AccountDatasource,
        appLinksProvider: AppLinksProvider,
        userDao: UserDao,
        nodeDao: NodeDao
    ): AccountRepository {
        return AccountRepositoryImpl(accountDatasource, userDao, nodeDao, Bip39(), SS58Encoder(), JunctionDecoder(), KeypairFactory(), appLinksProvider)
    }

    @Provides
    @FeatureScope
    fun provideAccountInteractor(
        accountRepository: AccountRepository
    ): AccountInteractor {
        return AccountInteractorImpl(accountRepository)
    }

    @Provides
    @FeatureScope
    fun provideAccountDatasource(
        preferences: Preferences,
        encryptedPreferences: EncryptedPreferences
    ): AccountDatasource {
        return AccountDatasourceImpl(preferences, encryptedPreferences)
    }
}