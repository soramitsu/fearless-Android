package jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RawDataViewModelTest {
    @get:Rule val executor = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val models = mutableListOf<RawDataViewModel>()

    @Before
    fun setDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanUp() {
        models.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun jsonIsPrettyPrintedWithoutChangingItsFields() = runTest(dispatcher) {
        val model = create("{\"id\":7,\"message\":\"sign me\"}")
        runCurrent()
        val formatted = model.state.value.rawData
        assertEquals(7, JSONObject(formatted).getInt("id"))
        assertEquals("sign me", JSONObject(formatted).getString("message"))
        assertEquals(true, formatted.contains("\n  "))
    }

    @Test
    fun unstructuredPayloadIsPreserved() = runTest(dispatcher) {
        val model = create("0x010203")
        runCurrent()
        assertEquals("0x010203", model.state.value.rawData)
    }

    @Test
    fun missingPayloadIsEmptyAndCloseNavigatesBack() = runTest(dispatcher) {
        val model = create(null)
        runCurrent()
        assertEquals("", model.state.value.rawData)
        model.onClose()
        verify(exactly = 1) { router.back() }
    }

    private fun create(raw: String?): RawDataViewModel = RawDataViewModel(
        SavedStateHandle(raw?.let { mapOf(RawDataFragment.RAW_DATA_KEY to it) } ?: emptyMap()), router
    ).also { models += it }
}
