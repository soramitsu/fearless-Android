package jp.co.soramitsu.liquiditypools.impl.presentation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<InternalPoolsRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.common_error_general_title) } returns "Error"
        every { getString(R.string.common_error_network) } returns "Network problem"
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failing ui job shows the network error and a cancelled scope is replaced`() = runTest(dispatcher) {
        val store = CoroutinesStore(resources, router)
        val ui = store.uiScope
        assertSame(ui, store.uiScope)

        ui.launch { error("boom") }
        advanceUntilIdle()

        verify(exactly = 1) { router.openErrorsScreen("Error", "Network problem") }
        ui.cancel()
        assertNotSame(ui, store.uiScope)
        assertTrue(store.uiScope.isActive)
    }

    @Test
    fun `a failing io job shows the same network error`() = runTest(dispatcher) {
        val store = CoroutinesStore(resources, router)

        store.ioScope.launch { error("io") }

        verify(timeout = 5_000, exactly = 1) { router.openErrorsScreen("Error", "Network problem") }
        assertSame(store.ioScope, store.ioScope)
    }
}
