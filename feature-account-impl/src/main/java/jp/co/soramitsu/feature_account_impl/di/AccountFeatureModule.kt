package jp.co.soramitsu.feature_account_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSourceImpl
import jp.co.soramitsu.feature_account_impl.domain.AccountInteractorImpl
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl.CryptoTypeChooser
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl.NetworkChooser

@Module
class AccountFeatureModule {

    @Provides
    @FeatureScope
    fun provideBip39() = Bip39()

    @Provides
    @FeatureScope
    fun provideSs58Encoder() = SS58Encoder()

    @Provides
    @FeatureScope
    fun provideJunctionDecoder() = JunctionDecoder()

    @Provides
    @FeatureScope
    fun provideKeyFactory() = KeypairFactory()

    @Provides
    @FeatureScope
    fun provideJsonDecoder(
        sS58Encoder: SS58Encoder,
        keypairFactory: KeypairFactory,
        jsonMapper: Gson
    ) = JsonSeedDecoder(jsonMapper, sS58Encoder, keypairFactory)

    @Provides
    @FeatureScope
    fun provideJsonMapper() = Gson()

    @Provides
    fun provideNetworkChooserMixin(interactor: AccountInteractor): NetworkChooserMixin =
        NetworkChooser(interactor)

    @Provides
    fun provideCryptoChooserMixin(
        interactor: AccountInteractor,
        resourceManager: ResourceManager
    ): CryptoTypeChooserMixin = CryptoTypeChooser(interactor, resourceManager)

    @Provides
    @FeatureScope
    fun provideAccountRepository(
        bip39: Bip39,
        sS58Encoder: SS58Encoder,
        junctionDecoder: JunctionDecoder,
        keypairFactory: KeypairFactory,
        accountDataSource: AccountDataSource,
        appLinksProvider: AppLinksProvider,
        accountDao: AccountDao,
        nodeDao: NodeDao,
        jsonSeedDecoder: JsonSeedDecoder,
        languagesHolder: LanguagesHolder
    ): AccountRepository {
        return AccountRepositoryImpl(
            accountDataSource,
            accountDao,
            nodeDao,
            bip39,
            sS58Encoder,
            junctionDecoder,
            keypairFactory,
            appLinksProvider,
            jsonSeedDecoder,
            languagesHolder
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