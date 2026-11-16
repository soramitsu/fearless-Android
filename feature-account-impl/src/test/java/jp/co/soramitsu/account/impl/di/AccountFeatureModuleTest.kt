package jp.co.soramitsu.account.impl.di

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActionsProvider
import jp.co.soramitsu.account.impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.account.impl.data.repository.KeyPairRepository
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSourceImpl
import jp.co.soramitsu.account.impl.domain.AccountInteractorImpl
import jp.co.soramitsu.account.impl.domain.AssetNotNeedAccountUseCaseImpl
import jp.co.soramitsu.account.impl.domain.NomisScoreInteractorImpl
import jp.co.soramitsu.account.impl.domain.UniversalWalletPasswordNoticeService
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.common.mixin.impl.CryptoTypeChooser
import jp.co.soramitsu.account.impl.presentation.google_signin.GoogleDriveSignInActivity
import jp.co.soramitsu.backup.passkey.OkHttpGoogleDriveHttpTransport
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountFeatureModuleTest {

    private val module = AccountFeatureModule()
    private val preferences = mockk<Preferences>(relaxed = true)
    private val metaAccountDao = mockk<MetaAccountDao>(relaxed = true)
    private val encryptedPreferences = mockk<EncryptedPreferences>(relaxed = true) {
        every { getDecryptedString(any()) } returns null
    }

    private val coordinator = module.provideWalletSecretMutationCoordinator(
        mockk<AppDatabase>(relaxed = true), metaAccountDao, mockk(relaxed = true), encryptedPreferences
    )
    private val freshRoots = module.provideUniversalWalletFreshRoots(preferences)
    private val watchList = module.provideUniversalWalletWatchList(preferences)

    private fun repository(dataSource: AccountDataSource): AccountRepository {
        val delegate = module.provideAccountRepositoryDelegate(
            module.provideSubstrateOrEvmAccountRepository(metaAccountDao, coordinator, freshRoots),
            module.providesTonAccountRepository(coordinator)
        )
        return module.provideAccountRepository(
            dataSource, metaAccountDao, mockk(relaxed = true), module.provideJsonDecoder(mockk(relaxed = true)),
            module.provideJsonEncoder(mockk(relaxed = true)), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), delegate,
            coordinator, mockk(relaxed = true), freshRoots, watchList
        )
    }

    @Test
    fun accountRepositoryIsTheProductionImplementationReadingTheGivenDataSource(): Unit = runBlocking {
        val dataSource = mockk<AccountDataSource>(relaxed = true)
        val account = mockk<MetaAccount>()
        coEvery { dataSource.getMetaAccount(5L) } returns account

        val repository = repository(dataSource)

        assertTrue(repository is AccountRepositoryImpl)
        assertSame(account, repository.getMetaAccount(5L))
    }

    @Test
    fun accountDataSourceStoresTheAuthTypeInTheGivenPreferences(): Unit = runBlocking {
        val dataSource = module.provideAccountDataSource(
            preferences, mockk(relaxed = true), mockk(relaxed = true), metaAccountDao, mockk(relaxed = true),
            mockk(relaxed = true)
        )

        dataSource.saveAuthType(AuthType.BIOMETRY)

        assertTrue(dataSource is AccountDataSourceImpl)
        verify { preferences.putString("auth_type", "BIOMETRY") }
    }

    @Test
    fun interactorKeyPairsAndNoticesAreBuiltOnTheSameRepository(): Unit = runBlocking {
        val repository = mockk<AccountRepository>(relaxed = true)
        coEvery { repository.isCodeSet() } returns true

        val interactor = module.provideAccountInteractor(
            repository, mockk(relaxed = true), preferences, mockk(relaxed = true), mockk(relaxed = true)
        )
        val keyPairs = module.provideKeyPairRepository(
            mockk(relaxed = true), repository, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true)
        )
        val notice = module.provideUniversalWalletPasswordNotice(
            repository, metaAccountDao, coordinator, mockk(relaxed = true), preferences, watchList
        )

        assertTrue(interactor is AccountInteractorImpl)
        assertTrue(interactor.isCodeSet())
        assertTrue(keyPairs is KeyPairRepository)
        assertTrue(notice is UniversalWalletPasswordNoticeService)
        assertTrue(module.provideNomisScoresInteractor(repository, preferences, mockk()) is NomisScoreInteractorImpl)
        assertTrue(module.provideCryptoChooserMixin(interactor, mockk(relaxed = true)) is CryptoTypeChooser)
    }

    @Test
    fun accountScopedUseCasesResolveNamesThroughTheRepository(): Unit = runBlocking {
        val repository = mockk<AccountRepository>(relaxed = true)
        val wallet = mockk<MetaAccount> { every { name } returns "Savings" }
        coEvery { repository.findMetaAccount(any()) } returns wallet

        val displayName = module.provideAddressDisplayUseCase(repository)("0x" + "11".repeat(32))

        assertEquals("Savings", displayName)
        assertTrue(module.provideAccountUpdateScope(repository) is AccountUpdateScope)
        assertTrue(
            module.provideAccountDetailsInteractor(repository, mockk(relaxed = true), mockk(relaxed = true))
                is AccountDetailsInteractor
        )
        assertTrue(
            module.provideAssetNotNeedAccountUseCase(
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
            ) is AssetNotNeedAccountUseCaseImpl
        )
        assertTrue(
            module.provideExternalAccountActions(
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
            ) is ExternalAccountActionsProvider
        )
    }

    @Test
    fun networkProvidersUseTheGivenApiCreatorAndValidateNodeHosts() {
        val creator = mockk<NetworkApiCreator>()
        val api = mockk<CoingeckoApi>()
        every { creator.create(CoingeckoApi::class.java, any(), any(), any(), any()) } returns api

        assertSame(api, module.provideCoingeckoApi(creator))
        assertTrue(module.provideAvailableFiatCurrenciesUseCase(api) is GetAvailableFiatCurrencies)
        assertTrue(module.provideNodeHostValidator().hostIsValid("wss://rpc.example.org"))
        assertFalse(module.provideNodeHostValidator().hostIsValid("not a host"))
    }

    @Test
    fun driveBackupSignInWithoutASessionOpensTheAppsAccountChooser(): Unit = runBlocking {
        val transport = module.provideGoogleDriveHttpTransport()
        val authorization = module.provideGoogleDriveAuthorization(transport, encryptedPreferences)
        val service = module.provideBackupService(ApplicationProvider.getApplicationContext(), authorization, transport)
        val launcher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val launched = slot<Intent>()
        every { launcher.launch(capture(launched)) } returns Unit

        val signedIn = service.authorize(launcher)

        assertTrue(transport is OkHttpGoogleDriveHttpTransport)
        assertFalse(signedIn)
        assertEquals(GoogleDriveSignInActivity::class.java.name, launched.captured.component?.className)
        assertEquals(
            "SELECT_ACCOUNT",
            launched.captured.getStringExtra("jp.co.soramitsu.account.GOOGLE_DRIVE_SIGN_IN_PROMPT")
        )
    }
}
