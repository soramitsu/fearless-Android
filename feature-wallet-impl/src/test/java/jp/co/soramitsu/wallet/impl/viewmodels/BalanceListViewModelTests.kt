package jp.co.soramitsu.wallet.impl.viewmodels

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.compose.component.SoraCardProgress
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.soracard.api.domain.SoraCardBasicStatus
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.BalanceListViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceListViewModelTests {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var walletInteractor: WalletInteractor

    @MockK
    private lateinit var chainInteractor: ChainInteractor

    @MockK
    private lateinit var addressIconGenerator: AddressIconGenerator

    @MockK
    private lateinit var walletRouter: WalletRouter

    @MockK
    private lateinit var getAvailableFiatCurrencies: GetAvailableFiatCurrencies

    @MockK
    private lateinit var selectedFiat: SelectedFiat

    @MockK
    private lateinit var accountInteractor: AccountInteractor

    @MockK
    private lateinit var nomisScoreInteractor: NomisScoreInteractor

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var clipboardManager: ClipboardManager

    @MockK
    private lateinit var currentAccountAddressUseCase: CurrentAccountAddressUseCase

    @MockK
    private lateinit var totalBalanceUseCase: TotalBalanceUseCase

    @MockK
    private lateinit var pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario

    @MockK
    private lateinit var nFTInteractor: NFTInteractor

    @MockK
    private lateinit var walletConnectInteractor: WalletConnectInteractor

    @MockK
    private lateinit var soraCardInteractor: SoraCardInteractor

    @MockK
    private lateinit var soraCardRouter: SoraCardRouter

    @MockK
    private lateinit var picture: PictureDrawable

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    @MockK
    private lateinit var tonConnectInteractor: TonConnectInteractor

    private lateinit var vm: BalanceListViewModel

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        every { coroutineManager.io } returns this.coroutineContext[CoroutineDispatcher]!!
        every { coroutineManager.default } returns this.coroutineContext[CoroutineDispatcher]!!
        coEvery { chainInteractor.getChainAssets() } returns listOf(createAsset())
        every { soraCardInteractor.basicStatus } returns MutableStateFlow(createSoraCard())
        every { soraCardInteractor.getSoraCardProgress() } returns SoraCardProgress.START
        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(123L to emptyList())
        every { chainInteractor.getChainsFlow() } returns flowOf(emptyList())
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(createMetaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns flowOf("")
        every { nFTInteractor.nftFiltersFlow() } returns flowOf(emptyMap())
        every { nFTInteractor.collectionsFlow(any(), any()) } returns flowOf(emptySequence())
        every { walletInteractor.getAssetManagementIntroPassed() } returns true
        coEvery { walletInteractor.saveAssetManagementIntroPassed() } just runs
        every { walletInteractor.selectedLightMetaAccountFlow() } returns flowOf(
            createLightMetaAccount()
        )
        coEvery { currentAccountAddressUseCase.invoke(any()) } returns ""
        every { totalBalanceUseCase.observe(any()) } returns flowOf(
            TotalBalance(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "e",
            )
        )
        every { nomisScoreInteractor.observeCurrentAccountScore() } returns flowOf(createNomis())
        every { walletInteractor.isShowGetSoraCard() } returns true
        every { soraCardInteractor.isShowBuyXor() } returns true
        coEvery { soraCardInteractor.initialize() } just runs
        every { walletInteractor.networkIssuesFlow() } returns flowOf(emptyMap())
        every { selectedFiat.flow() } returns flowOf("selected fiat")
        every { getAvailableFiatCurrencies.flow() } returns flowOf(emptyList())
        every { walletRouter.chainSelectorPayloadFlow } returns flowOf("")
        coEvery { walletInteractor.getSelectedMetaAccount() } returns createMetaAccount()
        every { walletInteractor.saveChainId(any(), any()) } just runs
        every { pendulumPreInstalledAccountsScenario.isPendulumMode(any()) } returns false
        coEvery { walletInteractor.getSavedChainId(any()) } returns ""

        mockkStatic("jp.co.soramitsu.common.address.AddressIconGeneratorKt")
        coEvery {
            addressIconGenerator.createAddressModel(
                accountAddress = any(),
                sizeInDp = any(),
                accountName = any(),
            )
        } returns AddressModel("", picture, "")
//        mockkObject(BalanceUpdateTrigger)
//        every { BalanceUpdateTrigger.observe() } returns flowOf("")
        vm = BalanceListViewModel(
            interactor = walletInteractor,
            chainInteractor = chainInteractor,
            addressIconGenerator = addressIconGenerator,
            router = walletRouter,
            getAvailableFiatCurrencies = getAvailableFiatCurrencies,
            selectedFiat = selectedFiat,
            accountInteractor = accountInteractor,
            nomisScoreInteractor = nomisScoreInteractor,
            resourceManager = resourceManager,
            clipboardManager = clipboardManager,
            currentAccountAddress = currentAccountAddressUseCase,
            getTotalBalance = totalBalanceUseCase,
            pendulumPreInstalledAccountsScenario = pendulumPreInstalledAccountsScenario,
            nftInteractor = nFTInteractor,
            walletConnectInteractor = walletConnectInteractor,
            soraCardInteractor = soraCardInteractor,
            soraCardRouter = soraCardRouter,
            coroutineManager = coroutineManager,
            tonConnectInteractor = tonConnectInteractor
        )
    }

    @Test
    fun `default state check`() = runTest {
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(true, state.soraCardState.visible)
    }

    private fun createNomis() = NomisScoreData(
        metaId = 1,
        score = 3,
        updated = 4,
        nativeBalanceUsd = BigDecimal.ZERO,
        holdTokensUsd = BigDecimal.ZERO,
        walletAgeInMonths = 4,
        totalTransactions = 9,
        rejectedTransactions = 5,
        avgTransactionTimeInHours = 8.3,
        maxTransactionTimeInHours = 4.7,
        minTransactionTimeInHours = 3.7,
        scoredAt = 33,
    )

    private fun createLightMetaAccount() = LightMetaAccount(
        id = 13,
        substratePublicKey = ByteArray(32),
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = ByteArray(32),
        ethereumAddress = ByteArray(32),
        ethereumPublicKey = ByteArray(32),
        tonPublicKey = ByteArray(32),
        isSelected = true,
        name = "name",
        isBackedUp = true,
        initialized = true,
    )

    private fun createMetaAccount() = MetaAccount(
        id = 12,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32),
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = ByteArray(32),
        ethereumAddress = ByteArray(32),
        ethereumPublicKey = ByteArray(32),
        tonPublicKey = ByteArray(32),
        isSelected = false,
        isBackedUp = true,
        googleBackupAddress = "",
        name = "",
        initialized = true,
    )

    private fun createSoraCard() = SoraCardBasicStatus(
        initialized = true,
        initError = null,
        availabilityInfo = null,
        verification = SoraCardCommonVerification.Started,
        needInstallUpdate = false,
        applicationFee = null,
        ibanInfo = null,
        phone = "+123",
    )

    private fun createAsset() = Asset(
        id = "0x0200000000000000000000000000000000000000000000000000000000000000",
        name = "name",
        symbol = "symbol",
        iconUrl = "icon url",
        chainId = "chain id",
        chainName = "chain name",
        chainIcon = "chain icon",
        isTestNet = true,
        priceId = "price id",
        precision = 18,
        staking = Asset.StakingType.RELAYCHAIN,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.SoraAsset,
        currencyId = "0x0200000000000000000000000000000000000000000000000000000000000000",
        existentialDeposit = "ex dep",
        color = "color",
        isNative = false,
    )
}
