package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionReconciler
import jp.co.soramitsu.wallet.impl.domain.CrossChainSubmissions
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.ReviewedPolkaswapBridgeInteractor
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import jp.co.soramitsu.xcm.ReviewedBridgeQuote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

/**
 * The cross-chain confirmation's secondary paths: navigation, the phishing alert, validation
 * warnings and errors, the reviewed bridge, invalid statuses and an unreadable submission record.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossChainConfirmViewModelCoverageTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @After
    fun clearSeal() = TransferSeal.clear()

    private class Harness(
        val viewModel: CrossChainConfirmViewModel,
        val router: WalletRouter,
        val externalActions: ExternalAccountActions.Presentation,
        val validity: TransferValidityChecks.Presentation,
        val bridge: ReviewedPolkaswapBridgeInteractor,
        val crossChainSubmissions: CrossChainSubmissions,
        val validations: AtomicInteger,
        val submissions: AtomicInteger,
        val seenConfirmations: MutableList<List<TransferValidationResult>>
    )

    @Test
    fun `a confirmation without a draft cannot open`() {
        val error = assertThrows(IllegalStateException::class.java) {
            CrossChainConfirmViewModel(
                interactor = mockk(relaxed = true),
                router = mockk(relaxed = true),
                addressIconGenerator = mockk(relaxed = true),
                externalAccountActions = mockk(relaxed = true),
                transferValidityChecks = mockk(relaxed = true),
                savedStateHandle = SavedStateHandle(),
                resourceManager = mockk(relaxed = true),
                currentAccountAddress = mockk(relaxed = true),
                validateTransferUseCase = mockk(relaxed = true),
                xcmInteractor = mockk(relaxed = true),
                reviewedBridgeInteractor = mockk(relaxed = true),
                accountInteractor = mockk(relaxed = true),
                crossChainSubmissions = mockk(relaxed = true)
            )
        }
        assertEquals("Required data not provided for send confirmation", error.message)
    }

    @Test
    fun `back leaves and copy offers the recipient on the destination explorers`() = runTest {
        val harness = harness()

        harness.viewModel.onNavigationClick()
        harness.viewModel.copyRecipientAddressClicked()
        advanceUntilIdle()

        verify { harness.router.back() }
        val payload = slot<ExternalAccountActions.Payload>()
        verify { harness.externalActions.showExternalActions(capture(payload)) }
        assertEquals(SENDER, payload.captured.value)
        assertTrue(payload.captured.explorers.isEmpty())
    }

    @Test
    fun `a flagged recipient opens the phishing alert that matches its type`() = runTest {
        val titles = mapOf(
            PhishingType.SCAM to R.string.scam_alert_title,
            PhishingType.EXCHANGE to R.string.exchange_alert_title,
            PhishingType.DONATION to R.string.donation_alert_title,
            PhishingType.SANCTIONS to R.string.sanction_alert_title,
            PhishingType.UNKNOWN to R.string.donation_alert_title
        )
        titles.forEach { (type, title) ->
            val harness = harness(phishingType = type)
            settle(harness) { harness.viewModel.state.value.toInfoItem != null }
            val clickState = harness.viewModel.state.value.toInfoItem?.clickState
            assertTrue(clickState is TitleValueViewState.ClickState.Value)
            assertEquals(CrossChainConfirmViewState.CODE_WARNING_CLICK, clickState?.identifier)

            harness.viewModel.onItemClick(0) // not the warning: nothing opens
            harness.viewModel.onItemClick(CrossChainConfirmViewState.CODE_WARNING_CLICK)
            advanceUntilIdle()

            val message = if (type == PhishingType.EXCHANGE) text(R.string.exchange_alert_message) else formatted(R.string.scam_alert_message_format)
            verify(exactly = 1) {
                harness.router.openAlert(
                    AlertViewState(
                        title = text(title),
                        message = message,
                        buttonText = text(R.string.top_up),
                        iconRes = R.drawable.ic_alert_16
                    )
                )
            }
        }
    }

    @Test
    fun `a validation warning waits for the user and a confirmed warning is not asked again`() = runTest {
        val warning = TransferValidationResult.ExistentialDepositWarning("1 DOT")
        var validation: Result<TransferValidationResult> = Result.success(warning)
        val harness = harness(validation = { validation })

        harness.viewModel.onNextClick()
        settle(harness) { harness.viewModel.openValidationWarningEvent.value != null }
        val (result, shown) = harness.viewModel.openValidationWarningEvent.value!!.peekContent()
        assertEquals(warning, result)
        assertEquals(text(CommonR.string.common_existential_warning_title), shown.message)
        assertEquals(0, harness.submissions.get())

        validation = Result.success(TransferValidationResult.Valid)
        settle(harness) { harness.viewModel.state.value.buttonState.enabled }
        harness.viewModel.warningConfirmed(result)
        settle(harness) { harness.submissions.get() >= 1 }

        assertEquals(listOf(emptyList(), listOf<TransferValidationResult>(warning)), harness.seenConfirmations.toList())
    }

    @Test
    fun `a blocking validation result is shown with its title and nothing is sent`() = runTest {
        val harness = harness(validation = { Result.success(TransferValidationResult.InsufficientBalance) })

        harness.viewModel.onNextClick()
        settle(harness) { harness.viewModel.errorWithTitleLiveData.value != null }

        assertEquals(
            text(CommonR.string.common_not_enough_funds_title) to text(CommonR.string.choose_amount_error_too_big),
            harness.viewModel.errorWithTitleLiveData.value?.peekContent()
        )
        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `a destination fee finer than the asset precision is refused before validation`() = runTest {
        val harness = harness(transferDraft = draftOf(destinationFee = BigDecimal("0.00000000001")))

        harness.viewModel.onNextClick()
        settle(harness) { harness.viewModel.errorLiveData.value != null }

        assertEquals("Rounding necessary", harness.viewModel.errorLiveData.value?.peekContent())
        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `a reviewed bridge transfer submits its confirmed quote without the XCM validation`() = runTest {
        val bridgeDraft = draftOf(
            providerId = "provider",
            routeId = "route",
            runtimeFingerprint = "runtime",
            executionFingerprint = "execution",
            originFeeInPlanks = "100",
            destinationFeeInPlanks = "0",
            effectiveMinimumInPlanks = "5"
        )
        val harness = harness(transferDraft = bridgeDraft)

        harness.viewModel.onNextClick()
        settle(harness) { runCatching { verify { harness.router.openOperationSuccess("0xbridge", "origin") } }.isSuccess }

        coVerify(exactly = 1) {
            harness.bridge.submit(
                walletId = 1L,
                providerId = "provider",
                routeId = "route",
                originChainId = "origin",
                destinationChainId = "dest",
                asset = any(),
                recipientAddress = SENDER,
                amount = BigDecimal("1"),
                confirmedQuote = ReviewedBridgeQuote(
                    providerId = "provider",
                    routeId = "route",
                    runtimeFingerprint = "runtime",
                    executionFingerprint = "execution",
                    originFeeInPlanks = BigInteger("100"),
                    destinationFeeInPlanks = BigInteger.ZERO,
                    effectiveMinimumInPlanks = BigInteger("5")
                )
            )
        }
        verify { harness.router.finishSendFlow() }
        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `an invalid transfer status shows the matching warning or error`() = runTest {
        val warningHarness = harness(
            submissionResult = Result.failure(NotValidTransferStatus(TransferValidityLevel.Warning.Status.WillRemoveAccount))
        )
        warningHarness.viewModel.onNextClick()
        settle(warningHarness) {
            runCatching { verify { warningHarness.validity.showTransferWarning(TransferValidityLevel.Warning.Status.WillRemoveAccount) } }.isSuccess
        }

        val errorHarness = harness(
            submissionResult = Result.failure(NotValidTransferStatus(TransferValidityLevel.Error.Status.NotEnoughFunds))
        )
        errorHarness.viewModel.onNextClick()
        settle(errorHarness) {
            runCatching { verify { errorHarness.validity.showTransferError(TransferValidityLevel.Error.Status.NotEnoughFunds) } }.isSuccess
        }

        verify(exactly = 0) { warningHarness.router.finishSendFlow() }
        verify(exactly = 0) { errorHarness.router.finishSendFlow() }
    }

    @Test
    fun `an unreadable pending record is reported and blocks nothing on screen`() = runTest {
        val harness = harness(pending = { throw IllegalStateException("database closed") })

        settle(harness) { harness.viewModel.errorLiveData.value != null }

        assertEquals(text(R.string.send_storage_unavailable), harness.viewModel.errorLiveData.value?.peekContent())
        assertNull(harness.viewModel.state.value.pendingMessage)
    }

    @Test
    fun `an unreadable included record is treated as no earlier transfer`() = runTest {
        val harness = harness(included = { throw IllegalStateException("database closed") })

        settle(harness) { harness.viewModel.state.value.buttonState.enabled }

        coVerify { harness.crossChainSubmissions.included(SCOPE) }
        assertNull(harness.viewModel.state.value.pendingMessage)
        assertFalse(harness.viewModel.state.value.sendAgainVisible)
    }

    @Test
    fun `a status check that cannot reach the network says so`() = runTest {
        val harness = harness(
            pending = { THIS_TRANSFER },
            outcome = { throw IllegalStateException("node unreachable") }
        )

        settle(harness) { harness.viewModel.state.value.pendingMessage == text(R.string.cross_chain_pending_unavailable) }

        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `done after an included transfer leaves even when its record cannot be released`() = runTest {
        val harness = harness(
            pending = { THIS_TRANSFER },
            outcome = { CrossChainSubmissionReconciler.Outcome.Included(THIS_TRANSFER, INCLUSION) },
            onDismiss = { throw IllegalStateException("database closed") }
        )
        settle(harness) { harness.viewModel.state.value.buttonState.text == text(R.string.common_done) }

        harness.viewModel.onNextClick()
        settle(harness) { runCatching { verify { harness.router.finishSendFlow() } }.isSuccess }

        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `send again does nothing without an included transfer of this draft`() = runTest {
        val harness = harness()

        harness.viewModel.onSendAgainClick()
        advanceUntilIdle()

        coVerify(exactly = 0) { harness.crossChainSubmissions.dismissIncluded(any()) }
        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `send again is refused while the first transfer's record cannot be updated`() = runTest {
        val harness = harness(
            included = { THIS_TRANSFER },
            onDismiss = { throw IllegalStateException("database closed") }
        )
        settle(harness) { harness.viewModel.state.value.sendAgainVisible }

        harness.viewModel.onSendAgainClick()
        settle(harness) { harness.viewModel.errorLiveData.value != null }

        assertEquals(text(R.string.send_storage_unavailable), harness.viewModel.errorLiveData.value?.peekContent())
        assertTrue(harness.viewModel.state.value.sendAgainVisible)
        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    /** Submission hops to a real thread (Dispatchers.Default); the coroutine work is virtual. */
    private fun TestScope.settle(harness: Harness, done: () -> Boolean) {
        repeat(200) {
            advanceUntilIdle()
            if (done()) return
            Thread.sleep(25)
        }
        throw AssertionError(
            "timed out: validations=${harness.validations.get()} submissions=${harness.submissions.get()}"
        )
    }

    @Suppress("LongParameterList")
    private fun TestScope.harness(
        submissionResult: Result<String> = Result.success("0xabc"),
        validation: () -> Result<TransferValidationResult> = { Result.success(TransferValidationResult.Valid) },
        phishingType: PhishingType? = null,
        pending: () -> CrossChainSubmissionJournal.Submission? = { null },
        outcome: () -> CrossChainSubmissionReconciler.Outcome = { CrossChainSubmissionReconciler.Outcome.Settled },
        included: () -> CrossChainSubmissionJournal.Submission? = { null },
        onDismiss: () -> Unit = {},
        transferDraft: CrossChainTransferDraft = draftOf()
    ): Harness {
        val dot = coreAsset()
        val origin = chain("origin", listOf(dot))
        val destination = chain("dest", emptyList())

        val interactor = mockk<WalletInteractor>(relaxed = true)
        every { interactor.observeAddressBook(any()) } returns flowOf(emptyList())
        coEvery { interactor.getChain("origin") } returns origin
        coEvery { interactor.getChain("dest") } returns destination
        every { interactor.assetFlow("origin", "dot") } returns flowOf(walletAsset(dot))
        coEvery { interactor.getSelectedMetaAccount() } returns metaAccount()
        every { interactor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        val accountInteractor = mockk<AccountInteractor>()
        coEvery { accountInteractor.getMetaAccount(1L) } returns metaAccount()

        val currentAccountAddress = mockk<CurrentAccountAddressUseCase>()
        coEvery { currentAccountAddress.invoke("origin") } returns SENDER

        val validations = AtomicInteger()
        val seenConfirmations = CopyOnWriteArrayList<List<TransferValidationResult>>()
        val validate = mockk<ValidateTransferUseCase>()
        coEvery {
            validate.validateExistentialDeposit(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            validations.incrementAndGet()
            seenConfirmations += arg<List<TransferValidationResult>>(CONFIRMED_VALIDATIONS_ARG).toList()
            validation()
        }

        val submissions = mockk<CrossChainSubmissions>()
        every { submissions.scope(1L, origin, any()) } returns SCOPE
        coEvery { submissions.pending(SCOPE) } answers { pending() }
        coEvery { submissions.check(SCOPE, any()) } answers { outcome() }
        coEvery { submissions.included(SCOPE) } answers { included() }
        coEvery { submissions.dismissIncluded(any()) } answers { onDismiss() }

        val submissionCount = AtomicInteger()
        val xcm = mockk<XcmInteractor>()
        coEvery { xcm.performCrossChainTransfer(any(), 1L) } answers {
            submissionCount.incrementAndGet()
            submissionResult
        }
        val bridge = mockk<ReviewedPolkaswapBridgeInteractor>()
        coEvery { bridge.submit(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers { Result.success("0xbridge") }
        val router = mockk<WalletRouter>(relaxed = true)
        val externalActions = mockk<ExternalAccountActions.Presentation>(relaxed = true)
        val validity = mockk<TransferValidityChecks.Presentation>(relaxed = true)
        // Each string is its resource id, so the test can tell the messages and labels apart.
        val resourceManager = mockk<ResourceManager>(relaxed = true)
        every { resourceManager.getString(any<Int>()) } answers { text(firstArg()) }
        every { resourceManager.getString(any(), *anyVararg()) } answers { formatted(firstArg()) }

        val viewModel = CrossChainConfirmViewModel(
            interactor = interactor,
            router = router,
            addressIconGenerator = mockk<AddressIconGenerator>(relaxed = true),
            externalAccountActions = externalActions,
            transferValidityChecks = validity,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    CrossChainConfirmFragment.KEY_DRAFT to transferDraft,
                    CrossChainConfirmFragment.KEY_PHISHING_TYPE to phishingType
                )
            ),
            resourceManager = resourceManager,
            currentAccountAddress = currentAccountAddress,
            validateTransferUseCase = validate,
            xcmInteractor = xcm,
            reviewedBridgeInteractor = bridge,
            accountInteractor = accountInteractor,
            crossChainSubmissions = submissions
        )
        advanceUntilIdle() // let the eager stateIn flows publish their values

        return Harness(
            viewModel = viewModel,
            router = router,
            externalActions = externalActions,
            validity = validity,
            bridge = bridge,
            crossChainSubmissions = submissions,
            validations = validations,
            submissions = submissionCount,
            seenConfirmations = seenConfirmations
        )
    }

    @Suppress("LongParameterList")
    private fun draftOf(
        destinationFee: BigDecimal = BigDecimal.ZERO,
        providerId: String? = null,
        routeId: String? = null,
        runtimeFingerprint: String? = null,
        executionFingerprint: String? = null,
        originFeeInPlanks: String? = null,
        destinationFeeInPlanks: String? = null,
        effectiveMinimumInPlanks: String? = null
    ) = CrossChainTransferDraft(
        amount = BigDecimal("1"),
        originChainId = "origin",
        destinationChainId = "dest",
        originFee = BigDecimal("0.01"),
        destinationFee = destinationFee,
        chainAssetId = "dot",
        recipientAddress = SENDER,
        tip = null,
        transferableTokenSymbol = "DOT",
        metaAccountId = 1L,
        providerId = providerId,
        routeId = routeId,
        runtimeFingerprint = runtimeFingerprint,
        executionFingerprint = executionFingerprint,
        originFeeInPlanks = originFeeInPlanks,
        destinationFeeInPlanks = destinationFeeInPlanks,
        effectiveMinimumInPlanks = effectiveMinimumInPlanks
    )

    private fun metaAccount(id: Long = 1L) = MetaAccount(
        id = id,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 1 },
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "wallet",
        initialized = true
    )

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

    private fun chain(id: String, assets: List<Asset>) = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = id,
        minSupportedVersion = null,
        assets = assets,
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )

    private companion object {
        const val SENDER = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        /** validateExistentialDeposit(amount, asset, chain, recipient, sender, fee, confirmedValidations, destinationFee). */
        const val CONFIRMED_VALIDATIONS_ARG = 6
        val SCOPE = CrossChainSubmissionJournal.Scope(1L, "11".repeat(32), "01".repeat(32))

        fun text(id: Int) = "res:$id"

        fun formatted(id: Int) = "fmt:$id"

        // The draft of these tests: 1 DOT (10 decimals) to SENDER on "dest", no destination fee.
        val THIS_TRANSFER = CrossChainSubmissionJournal.Submission(
            scope = SCOPE,
            details = CrossChainSubmissionJournal.Details(
                destinationChainId = "dest",
                assetId = "dot",
                symbol = "DOT",
                precision = 10,
                recipient = SENDER,
                amountInPlanks = BigInteger.TEN.pow(10)
            ),
            signedExtrinsic = "0x01020304",
            intent = "aa".repeat(32),
            transactionHash = "0x" + "dd".repeat(32),
            attemptId = "8d1e2f30-1111-4222-8333-944455556666",
            acknowledged = false,
            anchor = FinalizedExtrinsicReader.Anchor("0x" + "11".repeat(32), BigInteger.valueOf(1_000), "0x" + "bb".repeat(32)),
            validity = null
        )
        val INCLUSION = FinalizedExtrinsicReader.Inclusion(
            genesisHash = "0x" + "11".repeat(32),
            transactionHash = "0x" + "dd".repeat(32),
            blockHash = "0x" + "ee".repeat(32),
            blockNumber = BigInteger.valueOf(1_004),
            extrinsicIndex = 1,
            finalizedHeadHash = "0x" + "cc".repeat(32),
            finalizedHeadNumber = BigInteger.valueOf(1_010)
        )
    }
}
