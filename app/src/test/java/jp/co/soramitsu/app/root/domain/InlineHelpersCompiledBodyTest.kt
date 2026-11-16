package jp.co.soramitsu.app.root.domain

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import io.mockk.mockk

/**
 * Inline helpers are copied into their callers, so their own compiled bodies (what reflection or Java callers
 * reach) are exercised here: the authenticated-shell route resolution and the startup housekeeping guard.
 */
class InlineHelpersCompiledBodyTest {

    private fun method(owner: String, prefix: String): Method =
        Class.forName(owner).declaredMethods.single { it.name == prefix }.apply { isAccessible = true }

    @Test
    fun theShellOwnsItsRoutesAndOnlySheetsOverParentSheetsGoToTheParent() {
        val resolve = method("jp.co.soramitsu.app.root.navigation.AuthenticatedShellNavigationKt", "authenticatedDestinationTarget")
        fun target(shell: String?, contains: Boolean, parent: String?, overSheet: Boolean): Any? = resolve.invoke(
            null, shell,
            { _: Any? -> contains },
            { parent },
            { _: Any? -> overSheet }
        )
        assertEquals("shell", target("shell", contains = true, parent = "root", overSheet = false))
        assertEquals("root", target("shell", contains = true, parent = "root", overSheet = true))
        assertEquals("shell", target("shell", contains = true, parent = null, overSheet = true))
        assertNull("A route the shell does not own never escapes it", target("shell", contains = false, parent = "root", overSheet = true))
        assertEquals("root", target(null, contains = false, parent = "root", overSheet = false))
    }

    @Test
    fun housekeepingSwallowsOrdinaryFailuresButNotSecureStorageOrCancellation() {
        val housekeeping = method("jp.co.soramitsu.app.root.domain.WalletDatabaseStartupGateKt", "runStartupHousekeeping")
        var ran = false
        housekeeping.invoke(null, { ran = true })
        assertTrue(ran)
        housekeeping.invoke(null, { throw IllegalStateException("revalidation failed") })

        val storage = WalletSecureStorageUnavailableException("keystore", kind = WalletSecureStorageFailureKind.RETRYABLE)
        assertSame(storage, thrownBy { housekeeping.invoke(null, { throw RuntimeException("wrapped", storage) }) }.cause)
        assertTrue(thrownBy { housekeeping.invoke(null, { throw CancellationException("stop") }) } is CancellationException)
    }

    @Test
    fun anUnlockMaintenanceStepThatFailsLeavesTheNextStepToRun() {
        val maintenance = WalletUnlockMaintenance(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )
        val step = WalletUnlockMaintenance::class.java.getDeclaredMethod("step", Function0::class.java).apply { isAccessible = true }
        var ran = 0
        step.invoke(maintenance, { ran++ })
        step.invoke(maintenance, { throw IllegalStateException("sealing failed") })
        step.invoke(maintenance, { ran++ })
        assertEquals(2, ran)
        assertTrue(thrownBy { step.invoke(maintenance, { throw CancellationException("stop") }) } is CancellationException)
    }

    private fun thrownBy(block: () -> Unit): Throwable {
        try {
            block()
        } catch (failure: InvocationTargetException) {
            return failure.targetException
        }
        fail("Expected a failure")
        error("unreachable")
    }
}
