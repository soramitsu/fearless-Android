package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.select_market.SelectMarketFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>()
    private val markets = MutableSharedFlow<Market>(extraBufferCapacity = 1)
    private val models = mutableListOf<TransactionSettingsViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { router.observeResult<Market>(SelectMarketFragment.MARKET_KEY) } returns markets
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun model(initial: TransactionSettingsModel? = null): TransactionSettingsViewModel {
        val state = SavedStateHandle(initial?.let { mapOf(TransactionSettingsFragment.SETTINGS_MODEL_KEY to it) }.orEmpty())
        return TransactionSettingsViewModel(router, resources, state).also { models += it }
    }

    @Test fun defaultsAndSavedSettingsProduceCorrectMarketAndRoundedTolerance() = runTest(dispatcher) {
        val default = model()
        val restored = model(TransactionSettingsModel(Market.XYK, 1.26))
        runCurrent()
        assertEquals("SMART", default.state.value.marketState.subTitle)
        assertEquals("0.5", default.state.value.slippageInputState.value)
        assertNull(default.state.value.slippageWarningText)
        assertEquals("XYK", restored.state.value.marketState.subTitle)
        assertEquals("1.3", restored.state.value.slippageInputState.value)
    }

    @Test fun typedToleranceRejectsInvalidInputClampsBoundsAndUpdatesWarnings() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.onSlippageValueChange("invalid"); runCurrent()
        assertEquals("0.5", vm.state.value.slippageInputState.value)
        vm.onSlippageValueChange("-1"); runCurrent()
        assertEquals("0", vm.state.value.slippageInputState.value)
        assertEquals("string:${R.string.polkaswap_transaction_may_fail}", vm.state.value.slippageWarningText)
        assertTrue(vm.state.value.slippageInputState.warning)
        vm.onSlippageValueChange("15"); runCurrent()
        assertEquals("10", vm.state.value.slippageInputState.value)
        assertEquals("string:${R.string.polkaswap_transaction_may_frontrun}", vm.state.value.slippageWarningText)
        vm.onSlippageValueChange("1.5"); runCurrent()
        assertNull(vm.state.value.slippageWarningText)
        assertFalse(vm.state.value.slippageInputState.warning)
    }

    @Test fun selectingMarketAndSavingReturnsTheCurrentSettings() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        markets.emit(Market.TBC); runCurrent()
        vm.onQuickSlippageInput(0.1); runCurrent()
        assertEquals("TBC", vm.state.value.marketState.subTitle)
        assertEquals("0.1", vm.state.value.slippageInputState.value)
        vm.onSaveClick()
        verify { router.backWithResult(TransactionSettingsFragment.SETTINGS_MODEL_KEY to TransactionSettingsModel(Market.TBC, 0.1)) }
    }

    @Test fun focusIsClearedByMarketChooserSliderQuickInputAndReset() = runTest(dispatcher) {
        val vm = model(TransactionSettingsModel(Market.XYK, 5.0)); runCurrent()
        val focused = mockk<FocusState> { every { isFocused } returns true }
        vm.onAmountFocusChanged(focused); runCurrent()
        assertTrue(vm.state.value.slippageInputState.isFocused)
        vm.onMarketClick(); runCurrent()
        assertFalse(vm.state.value.slippageInputState.isFocused)
        verify { router.openSelectMarketDialog() }
        vm.onSlippageValueChange(2.34f); runCurrent()
        assertEquals("2.3", vm.state.value.slippageInputState.value)
        vm.onResetToDefaultClick(); runCurrent()
        assertEquals("SMART", vm.state.value.marketState.subTitle)
        assertEquals("0.5", vm.state.value.slippageInputState.value)
        assertNull(vm.state.value.slippageWarningText)
        vm.onCloseClick()
        verify { router.back() }
    }
}
