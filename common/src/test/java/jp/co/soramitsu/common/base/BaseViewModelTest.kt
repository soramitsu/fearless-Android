package jp.co.soramitsu.common.base

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.base.errors.ValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val model = BaseViewModel()

    @Before fun setMainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetDispatcher() { model.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test
    fun shouldPublishMessagesAndPreserveErrorTitles() {
        model.showMessage("Saved")
        assertEquals("Saved", model.messageLiveData.value?.getContentIfNotHandled())
        assertTrue(model.messageLiveData.value!!.hasBeenHandled)
        model.showError("Offline")
        assertEquals("Offline", model.errorLiveData.value?.peekContent())
        model.showError("Network", "Try later")
        assertEquals("Network" to "Try later", model.errorWithTitleLiveData.value?.peekContent())
        model.showError(ValidationException("Balance", "Insufficient"))
        assertEquals("Balance" to "Insufficient", model.errorWithTitleLiveData.value?.peekContent())
        model.showError(TitledException("Permission", "Denied"))
        assertEquals("Permission" to "Denied", model.errorWithTitleLiveData.value?.peekContent())
        model.showError(IllegalStateException("Failed"))
        val previous = model.errorLiveData.value
        assertEquals("Failed", previous?.peekContent())
        model.showError(Exception())
        assertSame(previous, model.errorLiveData.value)
        assertFalse(model.isSoftKeyboardOpenFlow.value)
    }

    @Test
    fun shouldPreserveDialogConfigurationAndCallbacks() {
        val calls = mutableListOf<String>()
        model.showError(title = "Problem", message = "Choose", positiveButtonText = "Retry", negativeButtonText = "Cancel",
            buttonsOrientation = 0, positiveClick = { calls += "retry" }, negativeClick = { calls += "cancel" },
            onBackClick = { calls += "back" }, isHideable = false)
        val state = model.errorDialogStateLiveData.value!!.peekContent()
        assertEquals("Problem", state.title)
        assertEquals("Choose", state.message)
        assertEquals("Retry", state.positiveButtonText)
        assertEquals("Cancel", state.negativeButtonText)
        assertEquals(0, state.buttonsOrientation)
        assertFalse(state.isHideable)
        state.positiveClick(); state.negativeClick(); state.onBackClick()
        assertEquals(listOf("retry", "cancel", "back"), calls)
    }

    @Test
    fun shouldShareTheLatestFlowValueAndBindLiveDataToTheViewModelScope() = runTest(dispatcher) {
        val source = MutableStateFlow("first")
        val shared = with(model) { source.share() }
        val live = with(model) { source.asLiveData() }
        val observed = mutableListOf<String>()
        val observer = Observer<String> { observed += it }
        live.observeForever(observer)
        try {
            runCurrent()
            assertEquals(listOf("first"), shared.replayCache)
            assertEquals("first", observed.last())
            source.value = "second"
            runCurrent()
            assertEquals(listOf("second"), shared.replayCache)
            assertEquals("second", observed.last())
            assertSame(model.viewModelScope.coroutineContext[Job], model.coroutineContext[Job])
        } finally { live.removeObserver(observer) }
    }
}
