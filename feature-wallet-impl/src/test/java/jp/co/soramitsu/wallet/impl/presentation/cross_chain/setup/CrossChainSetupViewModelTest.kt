package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

@OptIn(ExperimentalCoroutinesApi::class)
class CrossChainSetupViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Test
    fun `a fee-included route shows its destination fee as deducted, not as zero`() = runTest {
        val viewModel = viewModel(destinationFeeIncluded = true)

        assertEquals(
            "res:" + R.string.cross_chain_destination_fee_included,
            viewModel.state.value.destinationFeeInfoState?.feeAmount
        )
        assertEquals(null, viewModel.state.value.destinationFeeInfoState?.feeAmountFiat)
    }

    @Test
    fun `a fixed destination fee is shown as an amount`() = runTest {
        val viewModel = viewModel(destinationFeeIncluded = false)

        assertEquals("0 DOT", viewModel.state.value.destinationFeeInfoState?.feeAmount)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(destinationFeeIncluded: Boolean): CrossChainSetupViewModel {
        val chainAssetsManager = mockk<ChainAssetsManager>(relaxed = true)
        every { chainAssetsManager.originChainIdFlow } returns MutableStateFlow("origin")
        every { chainAssetsManager.destinationChainIdFlow } returns MutableStateFlow("dest")
        every { chainAssetsManager.assetIdFlow } returns MutableStateFlow("dot")
        every { chainAssetsManager.assetFlow } returns flowOf(walletAsset(coreAsset()))
        every { chainAssetsManager.originSelectedChain } returns flowOf(null)
        every { chainAssetsManager.destinationSelectedChainFlow } returns flowOf(null)
        every { chainAssetsManager.originChainSelectorStateFlow } returns flowOf(SelectorState.default)
        every { chainAssetsManager.destinationChainSelectorStateFlow } returns flowOf(SelectorState.default)
        coEvery { chainAssetsManager.routeCapability() } returns xcmCapability()
        coEvery { chainAssetsManager.routeInventory() } returns emptyList()
        val xcmInteractor = mockk<XcmInteractor>(relaxed = true)
        coEvery { xcmInteractor.getDestinationFee(any(), any(), any()) } returns BigDecimal.ZERO
        coEvery { xcmInteractor.getOriginFee(any(), any(), any(), any()) } returns BigDecimal("0.01")
        coEvery { xcmInteractor.isDestinationFeeIncluded(any(), any(), any()) } returns destinationFeeIncluded
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        coEvery { walletInteractor.getPhishingInfo(any()) } returns null
        val existentialDeposit = mockk<ExistentialDepositUseCase>()
        coEvery { existentialDeposit.invoke(any()) } returns Result.success(BigInteger.ZERO)
        val resourceManager = mockk<ResourceManager>(relaxed = true)
        every { resourceManager.getString(any<Int>()) } answers { "res:" + firstArg<Int>() }

        return CrossChainSetupViewModel(
            savedStateHandle = SavedStateHandle(),
            accountInteractor = mockk(relaxed = true),
            resourceManager = resourceManager,
            walletInteractor = walletInteractor,
            walletConstants = mockk(relaxed = true),
            router = mockk(relaxed = true),
            clipboardManager = mockk(relaxed = true),
            addressIconGenerator = mockk(relaxed = true),
            currentAccountAddress = mockk(relaxed = true),
            validateTransferUseCase = mockk(relaxed = true),
            chainAssetsManager = chainAssetsManager,
            xcmInteractor = xcmInteractor,
            reviewedBridgeInteractor = mockk(relaxed = true),
            existentialDepositUseCase = existentialDeposit,
            quickInputsUseCase = mockk(relaxed = true)
        ).also { advanceUntilIdle() }
    }

    private fun xcmCapability() = CrossChainRouteCapability(
        providerId = "wallet-reviewed-xcm",
        protocol = CrossChainProtocol.ReviewedXcm,
        availability = CrossChainRouteAvailability.Available,
        actionsEnabled = true,
        requiresAccount = true,
        requiresSigning = true
    )

    @Test
    fun `a route that left the discovery snapshot stops the transfer instead of crashing`() = runTest {
        val dot = coreAsset()
        val chainAssetsManager = mockk<ChainAssetsManager>(relaxed = true)
        every { chainAssetsManager.originChainIdFlow } returns MutableStateFlow("origin")
        every { chainAssetsManager.destinationChainIdFlow } returns MutableStateFlow("dest")
        every { chainAssetsManager.assetIdFlow } returns MutableStateFlow("dot")
        every { chainAssetsManager.assetFlow } returns flowOf(walletAsset(dot))
        every { chainAssetsManager.destinationChainId } returns "dest"
        coEvery { chainAssetsManager.routeCapability() } returns CrossChainRouteCapability(
            providerId = "wallet-reviewed-xcm",
            protocol = CrossChainProtocol.ReviewedXcm,
            availability = CrossChainRouteAvailability.Available,
            actionsEnabled = true,
            requiresAccount = true,
            requiresSigning = true
        )
        coEvery { chainAssetsManager.routeInventory() } returns emptyList()
        val xcmInteractor = mockk<XcmInteractor>(relaxed = true)
        coEvery { xcmInteractor.getDestinationFee(any(), any(), any()) } returns BigDecimal.ZERO
        coEvery { xcmInteractor.getOriginFee(any(), any(), any(), any()) } returns BigDecimal("0.01")
        // Each chain sync clears the discovery snapshot, so the route can disappear after the capability check.
        coEvery { xcmInteractor.getAmountMinLimit(any(), any(), any()) } throws
            IllegalArgumentException("XCM route does not support asset dot from origin to dest")
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        coEvery { walletInteractor.getPhishingInfo(any()) } returns null
        val accountInteractor = mockk<AccountInteractor>(relaxed = true)
        coEvery { accountInteractor.selectedMetaAccount() } returns mockk<MetaAccount> { every { id } returns 1L }
        val currentAccountAddress = mockk<CurrentAccountAddressUseCase>()
        coEvery { currentAccountAddress.invoke(any()) } returns "sender"
        val existentialDeposit = mockk<ExistentialDepositUseCase>()
        coEvery { existentialDeposit.invoke(any()) } returns Result.success(BigInteger.ZERO)
        val validate = mockk<ValidateTransferUseCase>(relaxed = true)
        val resourceManager = mockk<ResourceManager>(relaxed = true)
        every { resourceManager.getString(any<Int>()) } answers { "res:" + firstArg<Int>() }

        val viewModel = CrossChainSetupViewModel(
            savedStateHandle = SavedStateHandle(),
            accountInteractor = accountInteractor,
            resourceManager = resourceManager,
            walletInteractor = walletInteractor,
            walletConstants = mockk(relaxed = true),
            router = mockk(relaxed = true),
            clipboardManager = mockk(relaxed = true),
            addressIconGenerator = mockk(relaxed = true),
            currentAccountAddress = currentAccountAddress,
            validateTransferUseCase = validate,
            chainAssetsManager = chainAssetsManager,
            xcmInteractor = xcmInteractor,
            reviewedBridgeInteractor = mockk(relaxed = true),
            existentialDepositUseCase = existentialDeposit,
            quickInputsUseCase = mockk(relaxed = true)
        )
        advanceUntilIdle()

        viewModel.onNextClick()
        advanceUntilIdle()

        assertEquals("res:" + R.string.cross_chain_no_supported_route, viewModel.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { validate.validateXcmTransfer(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun coreAsset() = Asset(
        id = "dot",
        name = "dot",
        symbol = "DOT",
        iconUrl = "",
        chainId = "origin",
        chainName = "Origin",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 10,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.Normal,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true
    )

    private fun walletAsset(asset: Asset) = WalletAsset(
        metaId = 1L,
        token = Token(asset, fiatRate = null, fiatSymbol = "$", recentRateChange = null),
        accountId = ByteArray(32) { 1 },
        freeInPlanks = BigInteger("1000000000000"),
        reservedInPlanks = BigInteger.ZERO,
        miscFrozenInPlanks = BigInteger.ZERO,
        feeFrozenInPlanks = BigInteger.ZERO,
        bondedInPlanks = null,
        redeemableInPlanks = null,
        unbondingInPlanks = null,
        sortIndex = 0,
        enabled = null,
        minSupportedVersion = null,
        chainAccountName = null,
        markedNotNeed = false,
        status = null
    )
}
