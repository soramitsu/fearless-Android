package jp.co.soramitsu.staking.impl.presentation.staking.controller.set

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SetControllerViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<SetControllerViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stashId = ByteArray(32) { 9 }
    private val controllerId = ByteArray(32) { 8 }
    private val ownStash = StakingState.Stash.None(chain, stashId, controllerId, stashId)
    private val fromController = StakingState.Stash.None(chain, controllerId, controllerId, stashId)
    private val otherAddress = chain.addressOf(ByteArray(32) { 4 })
    private val drawable = PictureDrawable(Picture())
    private val appLinks = AppLinksProvider("terms", "privacy", "payouts", "tw/%s", "https://example.org/controller", "moonbeam")

    private val controller = mockk<ControllerInteractor>(relaxed = true)
    private val staking = mockk<StakingInteractor>(relaxed = true)
    private val relay = mockk<StakingRelayChainScenarioInteractor>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val router = mockk<StakingRouter>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val display = mockk<AddressDisplayUseCase>(relaxed = true)
    private val validationSystem = ValidationSystem(
        CompositeValidation<SetControllerValidationPayload, SetControllerValidationFailure>(emptyList())
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { external.openBrowserEvent } returns MutableLiveData()
        every { staking.currentAssetFlow() } returns flowOf(asset)
        every { staking.selectedChainFlow() } returns flowOf(chain)
        coEvery { staking.getChain(any()) } returns chain
        coEvery { staking.getUtilityAsset(any<Chain>()) } returns asset
        coEvery { staking.getAccountProjectionsInSelectedChains() } returns listOf(
            StakingAccount(otherAddress, "Other", false),
            StakingAccount(ownStash.stashAddress, null, false)
        )
        every { relay.selectedAccountStakingStateFlow() } returns flowOf(ownStash)
        every { relay.stakingStateFlow(any<String>()) } returns flowOf(ownStash)
        coEvery { controller.isControllerFeatureDeprecated(any()) } returns false
        coEvery { controller.estimateFee(any(), any(), any()) } returns BigInteger.TEN.pow(9)
        coEvery { display.invoke(any()) } returns "Wallet"
        coEvery { icons.createAddressIcon(any<ByteArray>(), any(), any()) } returns drawable
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun viewModel(chainId: String? = null) = SetControllerViewModel(
        interactor = controller,
        stakingInteractor = staking,
        relayChainInteractor = relay,
        addressIconGenerator = icons,
        router = router,
        externalActions = external,
        appLinksProvider = appLinks,
        resourceManager = resources,
        addressDisplayUseCase = display,
        validationExecutor = ValidationExecutor(resources),
        validationSystem = validationSystem,
        savedStateHandle = SavedStateHandle(chainId?.let { mapOf(SetControllerFragment.CHAIN_ID_KEY to it) } ?: emptyMap())
    ).also(owned::add)

    private fun SetControllerViewModel.loadedFee(): BigDecimal {
        waitUntil { feeLiveData.value is FeeStatus.Loaded }
        return (feeLiveData.value as FeeStatus.Loaded).feeModel.fee
    }

    @Test
    fun feeIsEstimatedOnTheSelectedChain() {
        val vm = viewModel()

        assertEquals(0, BigDecimal("0.1").compareTo(vm.loadedFee()))
        coVerify { controller.estimateFee(ownStash.stashAddress, ownStash.controllerAddress, chain.id) }
        verify { relay.selectedAccountStakingStateFlow() }
        assertNull(vm.chainId)
    }

    @Test
    fun explicitChainReadsThatChainsStakingState() {
        val vm = viewModel(chainId = chain.id)

        assertEquals(0, BigDecimal("0.1").compareTo(vm.loadedFee()))
        assertEquals(chain.id, vm.chainId)
        coVerify { staking.getChain(chain.id) }
        verify { relay.stakingStateFlow(chain.id) }
    }

    @Test
    fun feeFailureOffersRetryAndCancel() {
        coEvery { controller.estimateFee(any(), any(), any()) } throws IllegalStateException("node down")
        val vm = viewModel()

        waitUntil { vm.feeLiveData.value == FeeStatus.Error && vm.retryEvent.value != null }
        val retry = requireNotNull(vm.retryEvent.value).peekContent()
        assertEquals("s${R.string.choose_amount_network_error}", retry.title)
        assertEquals("s${R.string.choose_amount_error_fee}", retry.message)

        coEvery { controller.estimateFee(any(), any(), any()) } returns BigInteger.TEN.pow(10)
        requireNotNull(retry.onRetry).invoke()
        assertEquals(0, BigDecimal.ONE.compareTo(vm.loadedFee()))

        requireNotNull(retry.onCancel).invoke()
        verify { router.back() }
    }

    @Test
    fun currentControllerIsPreselectedAndOnlyAChangeEnablesContinue() {
        val vm = viewModel()
        vm.isContinueButtonAvailable.observeForever { }

        waitUntil {
            vm.controllerAccountModel.value != null && vm.stashAccountModel.value != null &&
                vm.isContinueButtonAvailable.value != null
        }
        assertEquals(ownStash.controllerAddress, vm.controllerAccountModel.value?.address)
        assertEquals("Wallet", vm.controllerAccountModel.value?.name)
        assertEquals(ownStash.stashAddress, vm.stashAccountModel.value?.address)
        assertEquals(false, vm.showNotStashAccountWarning.value)
        assertEquals(false, vm.isContinueButtonAvailable.value)

        vm.payoutControllerChanged(AddressModel(otherAddress, drawable))

        assertEquals(true, vm.isContinueButtonAvailable.value)
        assertTrue(runBlocking { vm.isControllerSelectionEnabled.first() })
    }

    @Test
    fun deprecatedControllerUsesTheStash() {
        coEvery { controller.isControllerFeatureDeprecated(any()) } returns true
        val vm = viewModel()

        waitUntil { vm.controllerAccountModel.value != null }
        assertEquals(ownStash.stashAddress, vm.controllerAccountModel.value?.address)
        assertFalse(runBlocking { vm.isControllerSelectionEnabled.first() })
    }

    @Test
    fun nonStashAccountShowsTheWarningAndKeepsContinueDisabled() {
        every { relay.selectedAccountStakingStateFlow() } returns flowOf(fromController)
        val vm = viewModel()
        vm.isContinueButtonAvailable.observeForever { }

        waitUntil { vm.controllerAccountModel.value != null && vm.showNotStashAccountWarning.value != null }
        assertEquals(true, vm.showNotStashAccountWarning.value)

        vm.payoutControllerChanged(AddressModel(otherAddress, drawable))

        waitUntil { vm.isContinueButtonAvailable.value != null }
        assertEquals(false, vm.isContinueButtonAvailable.value)
    }

    @Test
    fun learnMoreOpensTheControllerArticle() {
        val vm = viewModel()

        vm.onMoreClicked()

        assertEquals(appLinks.setControllerLearnMore, vm.openBrowserEvent.value?.peekContent())
    }

    @Test
    fun externalActionsShowTheStash() {
        val vm = viewModel()
        val payload = slot<ExternalAccountActions.Payload>()
        every { external.showExternalActions(capture(payload)) } returns Unit

        vm.openExternalActions()

        verify(timeout = 5_000) { external.showExternalActions(any()) }
        assertEquals(ownStash.stashAddress, payload.captured.value)
    }

    @Test
    fun openAccountsListsTheAccountsOfTheNetwork() {
        val vm = viewModel()

        vm.openAccounts()

        waitUntil { vm.showControllerChooserEvent.value != null }
        val accounts = requireNotNull(vm.showControllerChooserEvent.value).peekContent().data.toList()
        assertEquals(listOf(otherAddress, ownStash.stashAddress), accounts.map { it.address })
        assertEquals("Other", accounts.first().name)
        assertNull(accounts.last().name)
    }

    @Test
    fun continueBeforeTheFeeLoadsAsksToWait() {
        coEvery { controller.estimateFee(any(), any(), any()) } coAnswers { awaitCancellation() }
        val vm = viewModel()
        assertEquals(FeeStatus.Loading, vm.feeLiveData.value)

        vm.continueClicked()

        val (title, message) = requireNotNull(vm.errorWithTitleLiveData.value).peekContent()
        assertEquals("s${R.string.fee_not_yet_loaded_title}", title)
        assertEquals("s${R.string.fee_not_yet_loaded_message}", message)
        verify(exactly = 0) { router.openConfirmSetController(any()) }
    }

    @Test
    fun continueWithAChangedControllerOpensTheConfirmation() {
        val vm = viewModel(chainId = chain.id)
        val confirm = slot<ConfirmSetControllerPayload>()
        every { router.openConfirmSetController(capture(confirm)) } returns Unit
        vm.loadedFee()
        waitUntil { vm.controllerAccountModel.value != null }
        vm.payoutControllerChanged(AddressModel(otherAddress, drawable))

        vm.continueClicked()

        verify(timeout = 5_000) { router.openConfirmSetController(any()) }
        assertEquals(ownStash.stashAddress, confirm.captured.stashAddress)
        assertEquals(otherAddress, confirm.captured.controllerAddress)
        assertEquals(0, BigDecimal("0.1").compareTo(confirm.captured.fee))
        assertEquals(0, requireNotNull(asset.availableForStaking).compareTo(confirm.captured.transferable))
        assertEquals(chain.id, confirm.captured.chainId)
    }

    @Test
    fun backGoesBack() {
        viewModel().backClicked()

        verify { router.back() }
    }
}
