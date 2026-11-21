package jp.co.soramitsu.app.root.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedShellNavigationTest {

    @Test
    fun `missing nested child never attempts parent navigation`() {
        val shell = Any()
        var parentRequested = false

        val target = authenticatedDestinationTarget(
            shellController = shell,
            shellContainsDestination = { false },
            parentController = {
                parentRequested = true
                Any()
            }
        )

        assertNull(target)
        assertFalse(parentRequested)
    }

    @Test
    fun `parent remains available only before shell is mounted`() {
        val parent = Any()
        var parentRequested = false

        val target = authenticatedDestinationTarget(
            shellController = null,
            shellContainsDestination = { false },
            parentController = {
                parentRequested = true
                parent
            }
        )

        assertSame(parent, target)
        assertTrue(parentRequested)
    }

    @Test
    fun `a child the shell owns goes to the parent when it would open over a parent sheet`() {
        val shell = Any()
        val parent = Any()

        val target = authenticatedDestinationTarget(
            shellController = shell,
            shellContainsDestination = { true },
            parentController = { parent },
            opensOverParentSheet = { it === parent }
        )

        assertSame(parent, target)
    }

    @Test
    fun `a child the shell owns stays in the shell when no parent sheet shows`() {
        val shell = Any()

        val target = authenticatedDestinationTarget(
            shellController = shell,
            shellContainsDestination = { true },
            parentController = { Any() },
            opensOverParentSheet = { false }
        )

        assertSame(shell, target)
    }

    @Test
    fun `a child the shell lacks stays unresolved even over a parent sheet`() {
        var parentRequested = false

        val target = authenticatedDestinationTarget(
            shellController = Any(),
            shellContainsDestination = { false },
            parentController = {
                parentRequested = true
                Any()
            },
            opensOverParentSheet = { true }
        )

        assertNull(target)
        assertFalse(parentRequested)
    }
}
