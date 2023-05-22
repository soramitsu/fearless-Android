package jp.co.soramitsu.account.impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActionsProvider
import jp.co.soramitsu.account.impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.account.impl.data.repository.KeyPairRepository
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSourceImpl
import jp.co.soramitsu.account.impl.data.repository.datasource.migration.AccountDataMigration
import jp.co.soramitsu.account.impl.domain.AccountInteractorImpl
import jp.co.soramitsu.account.impl.domain.AssetNotNeedAccountUseCaseImpl
import jp.co.soramitsu.account.impl.domain.BeaconConnectedUseCase
import jp.co.soramitsu.account.impl.domain.NodeHostValidator
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.common.mixin.impl.CryptoTypeChooser
import jp.co.soramitsu.common.data.OnboardingStoriesDataSource
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.GetEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.ShouldShowEducationalStoriesUseCase
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.dao.AccountDao
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedEncoder
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AccountFeatureModule {

    @Provides
    fun provideJsonDecoder(jsonMapper: Gson) = JsonSeedDecoder(jsonMapper)

    @Provides
    fun provideJsonEncoder(
        jsonMapper: Gson
    ) = JsonSeedEncoder(jsonMapper)

    @Provides
    fun provideCryptoChooserMixin(
        interactor: AccountInteractor,
        resourceManager: ResourceManager
    ): CryptoTypeChooserMixin = CryptoTypeChooser(interactor, resourceManager)

    @Provides
    fun provideAccountRepository(
        accountDataSource: AccountDataSource,
        accountDao: AccountDao,
        metaAccountDao: MetaAccountDao,
        storeV2: SecretStoreV2,
        jsonSeedDecoder: JsonSeedDecoder,
        jsonSeedEncoder: JsonSeedEncoder,
        languagesHolder: LanguagesHolder,
        chainRegistry: ChainRegistry
    ): AccountRepository {
        return AccountRepositoryImpl(
            accountDataSource,
            accountDao,
            metaAccountDao,
            storeV2,
            jsonSeedDecoder,
            jsonSeedEncoder,
            languagesHolder,
            chainRegistry
        )
    }

    @Provides
    fun provideKeyPairRepository(
        secretStoreV2: SecretStoreV2,
        accountRepository: AccountRepository
    ): KeypairProvider {
        return KeyPairRepository(secretStoreV2, accountRepository)
    }

    @Provides
    fun provideAccountInteractor(
        accountRepository: AccountRepository,
        fileProvider: FileProvider
    ): AccountInteractor {
        return AccountInteractorImpl(accountRepository, fileProvider)
    }

    @Provides
    fun provideAccountDataSource(
        preferences: Preferences,
        encryptedPreferences: EncryptedPreferences,
        jsonMapper: Gson,
        secretStoreV1: SecretStoreV1,
        accountDataMigration: AccountDataMigration,
        metaAccountDao: MetaAccountDao,
        chainRegistry: ChainRegistry,
        secretStoreV2: SecretStoreV2
    ): AccountDataSource {
        return AccountDataSourceImpl(
            preferences,
            encryptedPreferences,
            jsonMapper,
            metaAccountDao,
            chainRegistry,
            secretStoreV2,
            secretStoreV1,
            accountDataMigration
        )
    }

    @Provides
    fun provideNodeHostValidator() = NodeHostValidator()

    @Provides
    fun provideAccountDataMigration(
        preferences: Preferences,
        encryptedPreferences: EncryptedPreferences,
        accountDao: AccountDao
    ): AccountDataMigration {
        return AccountDataMigration(preferences, encryptedPreferences, accountDao)
    }

    @Provides
    fun provideExternalAccountActions(
        clipboardManager: ClipboardManager,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager,
        chainRegistry: ChainRegistry
    ): ExternalAccountActions.Presentation {
        return ExternalAccountActionsProvider(clipboardManager, appLinksProvider, resourceManager, chainRegistry)
    }

    @Provides
    fun provideAccountUpdateScope(
        accountRepository: AccountRepository
    ) = AccountUpdateScope(accountRepository)

    @Provides
    fun provideAddressDisplayUseCase(
        accountRepository: AccountRepository
    ) = AddressDisplayUseCase(accountRepository)

    @Provides
    fun provideAccountUseCase(
        accountRepository: AccountRepository
    ) = SelectedAccountUseCase(accountRepository)

    @Provides
    fun provideAccountDetailsInteractor(
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        assetNotNeedAccountUseCase: AssetNotNeedAccountUseCase
    ) = AccountDetailsInteractor(
        accountRepository,
        chainRegistry,
        assetNotNeedAccountUseCase
    )

    @Provides
    fun provideCoingeckoApi(networkApiCreator: NetworkApiCreator): CoingeckoApi {
        return networkApiCreator.create(CoingeckoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAvailableFiatCurrenciesUseCase(coingeckoApi: CoingeckoApi) = GetAvailableFiatCurrencies(coingeckoApi)

    @Provides
    @Singleton
    fun provideSelectedFiatUseCase(preferences: Preferences) = SelectedFiat(preferences)

    @Provides
    fun provideAssetNotNeedAccountUseCase(
        assetDao: AssetDao,
        tokenPriceDao: TokenPriceDao
    ): AssetNotNeedAccountUseCase {
        return AssetNotNeedAccountUseCaseImpl(assetDao, tokenPriceDao)
    }

    @Provides
    fun provideStoriesDataSource() = OnboardingStoriesDataSource()

    @Provides
    fun provideShouldShowEducationalStories(
        preferences: Preferences
    ): ShouldShowEducationalStoriesUseCase {
        return ShouldShowEducationalStoriesUseCase(preferences)
    }

    @Provides
    fun provideGetEducationalStories(
        onboardingStoriesDataSource: OnboardingStoriesDataSource
    ): GetEducationalStoriesUseCase {
        return GetEducationalStoriesUseCase(onboardingStoriesDataSource)
    }

    @Provides
    fun provideBeaconConnectedUseCase(preferences: Preferences): BeaconConnectedUseCase {
        return BeaconConnectedUseCase(preferences)
    }
}
