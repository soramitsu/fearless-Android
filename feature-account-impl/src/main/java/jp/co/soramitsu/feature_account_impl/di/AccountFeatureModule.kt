package jp.co.soramitsu.feature_account_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.OnboardingStoriesDataSource
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.GetEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.ShouldShowEducationalStoriesUseCase
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.MetaAccountDao
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActionsProvider
import jp.co.soramitsu.feature_account_impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSourceImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.migration.AccountDataMigration
import jp.co.soramitsu.feature_account_impl.domain.AccountInteractorImpl
import jp.co.soramitsu.feature_account_impl.domain.AssetNotNeedAccountUseCaseImpl
import jp.co.soramitsu.feature_account_impl.domain.NodeHostValidator
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl.CryptoTypeChooser
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.rpc.RpcCalls

@Module
class AccountFeatureModule {

    @Provides
    @FeatureScope
    fun provideExtrinsicService(
        accountRepository: AccountRepository,
        secretStoreV2: SecretStoreV2,
        rpcCalls: RpcCalls,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    ): ExtrinsicService = ExtrinsicService(
        rpcCalls,
        accountRepository,
        secretStoreV2,
        extrinsicBuilderFactory
    )

    @Provides
    @FeatureScope
    fun provideJsonDecoder(jsonMapper: Gson) = JsonSeedDecoder(jsonMapper)

    @Provides
    @FeatureScope
    fun provideJsonEncoder(
        jsonMapper: Gson,
    ) = JsonSeedEncoder(jsonMapper)

    @Provides
    fun provideCryptoChooserMixin(
        interactor: AccountInteractor,
        resourceManager: ResourceManager
    ): CryptoTypeChooserMixin = CryptoTypeChooser(interactor, resourceManager)

    @Provides
    @FeatureScope
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
    @FeatureScope
    fun provideAccountInteractor(
        accountRepository: AccountRepository,
        fileProvider: FileProvider
    ): AccountInteractor {
        return AccountInteractorImpl(accountRepository, fileProvider)
    }

    @Provides
    @FeatureScope
    fun provideAccountDataSource(
        preferences: Preferences,
        encryptedPreferences: EncryptedPreferences,
        jsonMapper: Gson,
        secretStoreV1: SecretStoreV1,
        accountDataMigration: AccountDataMigration,
        metaAccountDao: MetaAccountDao,
        chainRegistry: ChainRegistry,
        secretStoreV2: SecretStoreV2,
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
    @FeatureScope
    fun provideAccountDataMigration(
        preferences: Preferences,
        encryptedPreferences: EncryptedPreferences,
        accountDao: AccountDao
    ): AccountDataMigration {
        return AccountDataMigration(preferences, encryptedPreferences, accountDao)
    }

    @Provides
    @FeatureScope
    fun provideExternalAccountActions(
        clipboardManager: ClipboardManager,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager,
        chainRegistry: ChainRegistry
    ): ExternalAccountActions.Presentation {
        return ExternalAccountActionsProvider(clipboardManager, appLinksProvider, resourceManager, chainRegistry)
    }

    @Provides
    @FeatureScope
    fun provideAccountUpdateScope(
        accountRepository: AccountRepository
    ) = AccountUpdateScope(accountRepository)

    @Provides
    @FeatureScope
    fun provideAddressDisplayUseCase(
        accountRepository: AccountRepository
    ) = AddressDisplayUseCase(accountRepository)

    @Provides
    @FeatureScope
    fun provideAccountUseCase(
        accountRepository: AccountRepository
    ) = SelectedAccountUseCase(accountRepository)

    @Provides
    @FeatureScope
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
    @FeatureScope
    fun provideCoingeckoApi(networkApiCreator: NetworkApiCreator): CoingeckoApi {
        return networkApiCreator.create(CoingeckoApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideAvailableFiatCurrenciesUseCase(coingeckoApi: CoingeckoApi) = GetAvailableFiatCurrencies(coingeckoApi)

    @Provides
    @FeatureScope
    fun provideSelectedFiatUseCase(preferences: Preferences) = SelectedFiat(preferences)

    @Provides
    @FeatureScope
    fun provideAssetNotNeedAccountUseCase(
        assetDao: AssetDao
    ): AssetNotNeedAccountUseCase {
        return AssetNotNeedAccountUseCaseImpl(assetDao)
    }

    @Provides
    @FeatureScope
    fun provideStoriesDataSource() = OnboardingStoriesDataSource()

    @Provides
    @FeatureScope
    fun provideShouldShowEducationalStories(
        preferences: Preferences
    ): ShouldShowEducationalStoriesUseCase {
        return ShouldShowEducationalStoriesUseCase(preferences)
    }

    @Provides
    @FeatureScope
    fun provideGetEducationalStories(
        onboardingStoriesDataSource: OnboardingStoriesDataSource
    ): GetEducationalStoriesUseCase {
        return GetEducationalStoriesUseCase(onboardingStoriesDataSource)
    }
}
