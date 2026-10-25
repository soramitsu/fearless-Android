package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionReconciler
import jp.co.soramitsu.wallet.impl.domain.CrossChainSubmissions
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.ReviewedPolkaswapBridgeInteractor
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

/**
 * Drives the real [CrossChainConfirmViewModel] with mocked collaborators. Every confirmed tap that
 * reaches submission signs a fresh extrinsic with a new nonce, so a second tap that arrives while the
 * first one is still validating or submitting must never move funds a second time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossChainConfirmViewModelSubmitTest {

    // showError publishes through LiveData, which needs the main looper that a JVM test does not have.
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private class Harness(
        val viewModel: CrossChainConfirmViewModel,
        val validations: AtomicInteger,
        val submissions: AtomicInteger,
        val router: WalletRouter,
        val crossChainSubmissions: CrossChainSubmissions
    )

    @Test
    fun `a fee-included destination fee is shown as deducted, not as zero`() = runTest {
        val harness = harness(submissionResult = Result.success("0xabc"), destinationFeeIncluded = true)

        settle(harness) { harness.viewModel.state.value.destinationFeeInfoItem != null }

        assertEquals(text(R.string.cross_chain_destination_fee_included), harness.viewModel.state.value.destinationFeeInfoItem?.value)
    }

    @Test
    fun `an earlier transfer of this draft that was included is never submitted again`() = runTest {
        val harness = harness(
            submissionResult = Result.success("0xabc"),
            pending = { THIS_TRANSFER },
            outcome = { CrossChainSubmissionReconciler.Outcome.Included(THIS_TRANSFER, INCLUSION) }
        )
        // The screen content waits for address icons drawn on a real thread.
        settle(harness) {
            harness.viewModel.state.value.pendingMessage == text(R.string.cross_chain_pending_included) &&
                harness.viewModel.state.value.buttonState.text == text(R.string.common_done)
        }

        harness.viewModel.onNextClick()
        settle(harness) { harness.viewModel.state.value.buttonState.enabled }
        advanceUntilIdle()

        verify { harness.router.finishSendFlow() }
        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    @Test
    fun `an ambiguous failure shows the transfer as unsettled instead of allowing a retry`() = runTest {
        var kept: CrossChainSubmissionJournal.Submission? = null
        val harness = harness(
            submissionResult = Result.failure(IllegalStateException("socket closed")),
            pending = { kept },
            outcome = { CrossChainSubmissionReconciler.Outcome.Unsettled(THIS_TRANSFER, BigInteger.valueOf(1_065)) },
            onSubmit = { kept = THIS_TRANSFER }
        )

        harness.viewModel.onNextClick()
        settle(harness) {
            harness.viewModel.state.value.pendingMessage == text(R.string.cross_chain_pending_message) &&
                harness.viewModel.state.value.buttonState.text == text(R.string.send_check_status)
        }

        harness.viewModel.onNextClick() // checks the status; it does not submit again
        settle(harness) { harness.viewModel.state.value.pendingMessage == text(R.string.cross_chain_pending_not_found) }

        assertEquals(1, harness.submissions.get())
        coVerify(exactly = 1) { harness.crossChainSubmissions.check(SCOPE, THIS_TRANSFER.attemptId) }
    }

    @Test
    fun `an earlier transfer that expired unsent can be sent again`() = runTest {
        val harness = harness(
            submissionResult = Result.success("0xabc"),
            pending = { THIS_TRANSFER },
            outcome = { CrossChainSubmissionReconciler.Outcome.Expired(THIS_TRANSFER) }
        )
        settle(harness) {
            harness.viewModel.state.value.pendingMessage == text(R.string.cross_chain_pending_expired) &&
                harness.viewModel.state.value.buttonState.text == text(R.string.common_confirm)
        }

        harness.viewModel.onNextClick()
        settle(harness) { harness.submissions.get() >= 1 }

        assertEquals(1, harness.validations.get())
    }

    @Test
    fun `two quick taps on Confirm validate and submit exactly once`() = runTest {
        val harness = harness(submissionResult = Result.success("0xabc"))

        harness.viewModel.onNextClick() // tap 1
        runCurrent() // tap 1 runs until its first suspension while the button is still enabled
        harness.viewModel.onNextClick() // tap 2, arrives before anything has been submitted

        settle(harness) { harness.submissions.get() >= 1 }
        // Give a (wrongly) admitted second tap time to arrive before asserting the negative.
        Thread.sleep(300)
        advanceUntilIdle()

        assertEquals("validations", 1, harness.validations.get())
        assertEquals("submissions", 1, harness.submissions.get())
    }

    @Test
    fun `a transfer reviewed for another wallet is neither validated nor submitted`() = runTest {
        val harness = harness(submissionResult = Result.success("0xabc"), selectedWalletId = 2L)

        harness.viewModel.onNextClick()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(0, harness.validations.get())
        assertEquals(0, harness.submissions.get())
        assertEquals(false, harness.viewModel.state.value.buttonState.enabled)
    }

    @Test
    fun `the confirm screen names the wallet that signs`() = runTest {
        val harness = harness(submissionResult = Result.success("0xabc"))

        // Address icons are drawn on Dispatchers.Default, a real thread.
        settle(harness) { harness.viewModel.state.value.fromInfoItem != null }
        val from = harness.viewModel.state.value.fromInfoItem

        assertEquals("wallet", from?.value)
    }

    @Test
    fun `a failed submission releases the guard so the user can retry`() = runTest {
        val harness = harness(submissionResult = Result.failure(IllegalStateException("socket closed")))

        harness.viewModel.onNextClick()
        settle(harness) { harness.submissions.get() >= 1 }
        Thread.sleep(100)
        advanceUntilIdle()
        assertEquals(1, harness.submissions.get())

        harness.viewModel.onNextClick()
        settle(harness) { harness.submissions.get() >= 2 }

        assertEquals("explicit retry after a finished attempt", 2, harness.submissions.get())
    }

    @Test
    fun `a blocking validation result releases the guard`() = runTest {
        val harness = harness(
            submissionResult = Result.success("0xabc"),
            validationResult = Result.failure(IllegalStateException("destination runtime unavailable"))
        )

        harness.viewModel.onNextClick()
        settle(harness) { harness.validations.get() >= 1 }
        Thread.sleep(100)
        advanceUntilIdle()
        assertEquals(0, harness.submissions.get())

        harness.viewModel.onNextClick()
        settle(harness) { harness.validations.get() >= 2 }

        assertEquals("the second explicit attempt reaches validation again", 2, harness.validations.get())
        assertEquals(0, harness.submissions.get())
    }

    /** Submission hops to a real thread (Dispatchers.Default); the validation delay is virtual. */
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

    private fun TestScope.harness(
        submissionResult: Result<String>,
        validationResult: Result<TransferValidationResult> = Result.success(TransferValidationResult.Valid),
        selectedWalletId: Long = 1L,
        pending: () -> CrossChainSubmissionJournal.Submission? = { null },
        outcome: () -> CrossChainSubmissionReconciler.Outcome = { CrossChainSubmissionReconciler.Outcome.Settled },
        onSubmit: () -> Unit = {},
        destinationFeeIncluded: Boolean = false
    ): Harness {
        val dot = coreAsset()
        val origin = chain("origin", listOf(dot))
        val destination = chain("dest", emptyList())

        val interactor = mockk<WalletInteractor>(relaxed = true)
        every { interactor.observeAddressBook(any()) } returns flowOf(emptyList())
        coEvery { interactor.getChain("origin") } returns origin
        coEvery { interactor.getChain("dest") } returns destination
        every { interactor.assetFlow("origin", "dot") } returns flowOf(walletAsset(dot))
        coEvery { interactor.getSelectedMetaAccount() } returns metaAccount(selectedWalletId)
        every { interactor.selectedMetaAccountFlow() } returns flowOf(metaAccount(selectedWalletId))
        val accountInteractor = mockk<AccountInteractor>()
        coEvery { accountInteractor.getMetaAccount(1L) } returns metaAccount()

        val currentAccountAddress = mockk<CurrentAccountAddressUseCase>()
        coEvery { currentAccountAddress.invoke("origin") } returns SENDER

        val validations = AtomicInteger()
        val validate = mockk<ValidateTransferUseCase>()
        coEvery {
            validate.validateExistentialDeposit(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            validations.incrementAndGet()
            delay(50) // stands for the network and database round trips inside the real validation
            validationResult
        }

        val submissions = mockk<CrossChainSubmissions>()
        every { submissions.scope(1L, origin, any()) } returns SCOPE
        coEvery { submissions.pending(SCOPE) } answers { pending() }
        coEvery { submissions.check(SCOPE, any()) } answers { outcome() }

        val submissionCount = AtomicInteger()
        val xcm = mockk<XcmInteractor>()
        coEvery { xcm.performCrossChainTransfer(any(), 1L) } answers {
            submissionCount.incrementAndGet()
            onSubmit()
            submissionResult
        }
        val router = mockk<WalletRouter>(relaxed = true)
        // Each string is its resource id, so the test can tell the messages and labels apart.
        val resourceManager = mockk<ResourceManager>(relaxed = true)
        every { resourceManager.getString(any<Int>()) } answers { text(firstArg()) }

        val draft = CrossChainTransferDraft(
            amount = BigDecimal("1"),
            originChainId = "origin",
            destinationChainId = "dest",
            originFee = BigDecimal("0.01"),
            destinationFee = BigDecimal.ZERO,
            chainAssetId = "dot",
            recipientAddress = SENDER,
            tip = null,
            transferableTokenSymbol = "DOT",
            metaAccountId = 1L,
            destinationFeeIncluded = destinationFeeIncluded
        )
        val viewModel = CrossChainConfirmViewModel(
            interactor = interactor,
            router = router,
            addressIconGenerator = mockk<AddressIconGenerator>(relaxed = true),
            externalAccountActions = mockk<ExternalAccountActions.Presentation>(relaxed = true),
            transferValidityChecks = mockk<TransferValidityChecks.Presentation>(relaxed = true),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    CrossChainConfirmFragment.KEY_DRAFT to draft,
                    CrossChainConfirmFragment.KEY_PHISHING_TYPE to null
                )
            ),
            resourceManager = resourceManager,
            currentAccountAddress = currentAccountAddress,
            validateTransferUseCase = validate,
            xcmInteractor = xcm,
            reviewedBridgeInteractor = mockk<ReviewedPolkaswapBridgeInteractor>(relaxed = true),
            accountInteractor = accountInteractor,
            crossChainSubmissions = submissions
        )
        advanceUntilIdle() // let the eager stateIn flows publish their values

        return Harness(viewModel, validations, submissionCount, router, submissions)
    }

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
        val SCOPE = CrossChainSubmissionJournal.Scope(1L, "11".repeat(32), "01".repeat(32))

        fun text(id: Int) = "res:$id"

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
