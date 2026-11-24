package jp.co.soramitsu.common.data.storage.pin

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSessionLockTest {
    @After fun tearDown() {
        WalletViewOnlyAccess.pinResetCompleted()
        WalletSessionLock.lock()
    }

    @Test fun `a new process starts locked`() {
        WalletSessionLock.lock()
        assertTrue(WalletSessionLock.isLocked())
        assertTrue(WalletSessionLock.locked.value)
    }

    @Test fun `unlock opens the wallet and lock closes it again`() {
        WalletSessionLock.unlock()
        assertFalse(WalletSessionLock.isLocked())
        WalletSessionLock.lock()
        assertTrue(WalletSessionLock.isLocked())
    }

    @Test fun `view-only access counts as locked even after an unlock`() {
        WalletSessionLock.unlock()
        WalletViewOnlyAccess.enter()
        assertTrue(WalletSessionLock.isLocked())
        WalletViewOnlyAccess.pinResetCompleted()
        assertFalse(WalletSessionLock.isLocked())
    }

    @Test fun `awaitUnlocked returns at once when unlocked`() = runBlocking {
        WalletSessionLock.unlock()
        withTimeout(1_000) { WalletSessionLock.awaitUnlocked() }
    }

    @Test fun `awaitUnlocked waits for the unlock`() = runBlocking {
        WalletSessionLock.lock()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { WalletSessionLock.awaitUnlocked() }
        yield()
        assertFalse(waiter.isCompleted)
        WalletSessionLock.unlock()
        withTimeout(1_000) { waiter.await() }
    }

    @Test fun `awaitUnlocked keeps waiting while the wallet is view-only`() = runBlocking {
        WalletSessionLock.lock()
        WalletViewOnlyAccess.enter()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { WalletSessionLock.awaitUnlocked() }
        WalletSessionLock.unlock()
        yield()
        assertFalse(waiter.isCompleted)
        WalletViewOnlyAccess.pinResetCompleted()
        withTimeout(1_000) { waiter.await() }
    }
}
