package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolkaswapDisclaimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val resources = mockk<ResourceManager>()
    private val interactor = mockk<PolkaswapInteractor>(relaxed = true)
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val models = mutableListOf<PolkaswapDisclaimerViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "s:${firstArg<Int>()}" }
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun model(alreadyRead: Boolean, resultDestination: Int? = null): PolkaswapDisclaimerViewModel {
        every { interactor.hasReadDisclaimer } returns alreadyRead
        val handle = SavedStateHandle(
            resultDestination?.let { mapOf(PolkaswapDisclaimerFragment.KEY_RESULT_DESTINATION to it) }.orEmpty()
        )
        return PolkaswapDisclaimerViewModel(resources, interactor, router, handle).also { models += it }
    }

    @Test fun firstVisitShowsTheTermsWithLinksAndAnUncheckedSwitch() = runTest(dispatcher) {
        val state = model(alreadyRead = false).state.value

        val links = listOf(
            "https://wiki.sora.org/polkaswap/polkaswap-faq",
            "https://wiki.sora.org/ecosystem/what-is-polkaswap/terms",
            "https://wiki.sora.org/ecosystem/what-is-polkaswap/privacy"
        )
        assertEquals("s:${R.string.polkaswap_info_text_1}", state.polkaswapMaintained.text)
        assertEquals("%%", state.polkaswapMaintained.delimiter)
        assertEquals(links, state.polkaswapMaintained.schema.map { it.link })
        assertTrue(state.polkaswapMaintained.schema.all { it.color == colorAccent })
        assertEquals("s:${R.string.polkaswap_info_text_6}", state.disclaimerReminder.text)
        assertEquals(links, state.disclaimerReminder.schema.map { it.link })
        assertEquals("s:${R.string.polkaswap_info_text_2}", state.userResponsibilityTitle)
        assertEquals(
            listOf(R.string.polkaswap_info_text_3, R.string.polkaswap_info_text_4, R.string.polkaswap_info_text_5).map { "s:$it" },
            state.userResponsibilities
        )
        assertFalse(state.hasReadChecked)
        assertTrue(state.switchEnabled)
    }

    @Test fun acceptedDisclaimerIsShownCheckedAndLocked() = runTest(dispatcher) {
        val vm = model(alreadyRead = true)
        runCurrent()

        assertTrue(vm.state.value.hasReadChecked)
        assertFalse(vm.state.value.switchEnabled)
    }

    @Test fun switchTogglesTheCheckedState() = runTest(dispatcher) {
        val vm = model(alreadyRead = false)
        vm.onHasReadChecked(); runCurrent()
        assertTrue(vm.state.value.hasReadChecked)
        vm.onHasReadChecked(); runCurrent()
        assertFalse(vm.state.value.hasReadChecked)
    }

    @Test fun continueRecordsAcceptanceAndReturnsToTheRequestingScreen() = runTest(dispatcher) {
        val vm = model(alreadyRead = false, resultDestination = 42)

        vm.onContinueClick()

        verify { interactor.hasReadDisclaimer = true }
        verify { router.backWithResult(42, PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT to true) }
    }

    @Test fun continueWithoutARequestingScreenChangesNothing() = runTest(dispatcher) {
        val vm = model(alreadyRead = false)

        vm.onContinueClick()

        verify(exactly = 0) { interactor.hasReadDisclaimer = any() }
        verify(exactly = 0) { router.backWithResult(any<Int>(), *anyVararg<Pair<String, Any?>>()) }
    }

    @Test fun backReportsTheDisclaimerAsNotRead() = runTest(dispatcher) {
        model(alreadyRead = false).onBackClick()

        verify { router.backWithResult(PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT to false) }
    }

    @Test fun linksOpenInTheWebViewerUnderThePolkaswapTitle() = runTest(dispatcher) {
        model(alreadyRead = false).onLinkClick("https://wiki.sora.org/polkaswap/polkaswap-faq")

        verify { router.openWebViewer("s:${R.string.tabbar_polkaswap_title}", "https://wiki.sora.org/polkaswap/polkaswap-faq") }
    }
}
