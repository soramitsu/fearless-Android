package jp.co.soramitsu.androidfoundation.coroutine

import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineManagerTest {
    @get:Rule
    val mainRule = MainCoroutineRule()

    @Test
    fun defaultManagerExposesThePlatformDispatchers() {
        val manager = CoroutineManager()
        assertSame(Dispatchers.IO, manager.io)
        assertSame(Dispatchers.Default, manager.default)
        assertSame(Dispatchers.Main, manager.main)
    }

    @Test
    fun injectedDispatchersAndMainRuleShareTheControlledScheduler() {
        val manager = CoroutineManager(mainRule.dispatcher, mainRule.dispatcher, Dispatchers.Main)
        assertSame(mainRule.dispatcher, manager.io)
        assertSame(mainRule.dispatcher, manager.default)
        var completed = false
        val job = CoroutineScope(manager.main).launch { completed = true }
        assertFalse(completed)
        mainRule.dispatcher.scheduler.runCurrent()
        assertTrue(completed)
        assertTrue(job.isCompleted)
    }
}
