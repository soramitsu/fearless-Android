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
}
