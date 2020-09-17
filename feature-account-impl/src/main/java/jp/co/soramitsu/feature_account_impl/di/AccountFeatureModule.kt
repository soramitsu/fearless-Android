package jp.co.soramitsu.feature_account_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSourceImpl
import jp.co.soramitsu.feature_account_impl.domain.AccountInteractorImpl
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountListingProvider
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl.CryptoTypeChooser
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl.NetworkChooser

@Module
class AccountFeatureModule {

    @Provides
    fun provideNetworkChooserMixin(interactor: AccountInteractor): NetworkChooserMixin =
        NetworkChooser(interactor)

    @Provides
    fun provideCryptoChooserMixin(
        interactor: AccountInteractor,
        resourceManager: ResourceManager
    ): CryptoTypeChooserMixin = CryptoTypeChooser(interactor, resourceManager)

    @Provides
    fun provideAccountListingMixin(
        interactor: AccountInteractor,
        iconGenerator: IconGenerator
    ): AccountListingMixin = AccountListingProvider(interactor, iconGenerator)

    @Provides
    @FeatureScope
    fun provideJsonMapper() = Gson()

    @Provides
    @FeatureScope
    fun provideAccountRepository(
        accountDataSource: AccountDataSource,
        appLinksProvider: AppLinksProvider,
        accountDao: AccountDao,
        nodeDao: NodeDao
    ): AccountRepository {
        return AccountRepositoryImpl(
            accountDataSource,
            accountDao,
            nodeDao,
            Bip39(),
            SS58Encoder(),
            JunctionDecoder(),
            KeypairFactory(),
            appLinksProvider
        )
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
        encryptedPreferences: EncryptedPreferences,
        jsonMapper: Gson
    ): AccountDataSource {
        return AccountDataSourceImpl(preferences, encryptedPreferences, jsonMapper)
    }
}