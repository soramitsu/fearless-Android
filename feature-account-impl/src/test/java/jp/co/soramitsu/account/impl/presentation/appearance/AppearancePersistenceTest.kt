package jp.co.soramitsu.account.impl.presentation.appearance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.compose.theme.fw.FwAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwAppearancePreferences
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearancePersistenceTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val main = StandardTestDispatcher()
    private val writes = NewestFirstDispatcher()
    private val preferences = mockk<FwAppearancePreferences>()
    private var persisted = FwAppearance()
    private var failuresRemaining = 0
    private lateinit var viewModel: AppearanceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(main)
        every { preferences.read() } answers { persisted }
        every { preferences.save(any()) } answers {
            if (failuresRemaining > 0) {
                failuresRemaining--
                false
            } else {
                persisted = firstArg()
                true
            }
        }
        viewModel = AppearanceViewModel(mockk(relaxed = true), preferences, writes)
    }

    @After
    fun teardown() {
        viewModel.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun latestCombinedChoiceSurvivesOutOfOrderIoScheduling() = runTest(main) {
        viewModel.onReducedChanged(true)
        viewModel.onContrastChanged(true)
        finishWrites()

        val expected = FwAppearance(FwTierChoice.Reduced, highContrast = true)
        assertEquals(expected, persisted)
        assertEquals(AppearanceState(expected), viewModel.state.value)
    }

    @Test
    fun oldFailureCannotOverrideSuccessfulReselectionOfTheSameValue() = runTest(main) {
        failuresRemaining = 1
        viewModel.onReducedChanged(true)
        viewModel.onReducedChanged(false)
        viewModel.onReducedChanged(true)
        finishWrites()

        assertEquals(FwAppearance(FwTierChoice.Reduced), persisted)
        assertFalse(viewModel.state.value.saveFailed)
    }

    @Test
    fun latestFailureIsVisibleAndRetryPersistsTheChosenSettings() = runTest(main) {
        failuresRemaining = 1
        viewModel.onContrastChanged(true)
        finishWrites()
        assertTrue(viewModel.state.value.saveFailed)
        assertEquals(FwAppearance(), persisted)

        viewModel.retry()
        finishWrites()
        assertEquals(FwAppearance(highContrast = true), persisted)
        assertFalse(viewModel.state.value.saveFailed)
    }

    private fun TestScope.finishWrites() {
        runCurrent()
        while (writes.pending.isNotEmpty()) {
            writes.pending.removeLast().run()
            runCurrent()
        }
    }

    /** IO workers may start later requests before earlier ones; force that ordering deterministically. */
    private class NewestFirstDispatcher : CoroutineDispatcher() {
        val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.addLast(block)
        }
    }
}
