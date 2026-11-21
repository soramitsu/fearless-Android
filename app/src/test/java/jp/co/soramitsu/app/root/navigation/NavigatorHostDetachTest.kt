package jp.co.soramitsu.app.root.navigation

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A newer root can attach before an older one is destroyed. The older one leaving must not take the newer one's
 * navigation with it: in 229 that left the Welcome screen without a controller and "Import" closed the app.
 */
class NavigatorHostDetachTest {
    private val access = mock<SavedEndpointAccess>()
    private val olderHost = mock<AppCompatActivity>()
    private val newerHost = mock<AppCompatActivity>()
    private val olderRoot = mock<NavController>()
    private val newerRoot = mock<NavController>()
    private val newerStack = MutableStateFlow(mock<NavBackStackEntry>())

    @Test fun `an older host leaving keeps the newer host's navigation`() {
        whenever(newerRoot.currentBackStackEntryFlow).thenReturn(newerStack)
        val navigator = Navigator(access)
        navigator.attach(olderRoot, olderHost)
        navigator.attach(newerRoot, newerHost)

        navigator.detach(olderHost)

        assertSame(newerStack, navigator.getCurrentBackStackEntryFlow())
        navigator.back()
        verify(newerRoot).popBackStack()
        // Never the newer root's listener: it still decides when dApp requests can open.
        verify(newerRoot, never()).removeOnDestinationChangedListener(any())
        // Private endpoint access is revoked whichever host leaves.
        verify(access).revokeAll()
    }

    @Test fun `the owning host leaving clears navigation without a crash`() = runBlocking {
        val navigator = Navigator(access)
        navigator.attach(newerRoot, newerHost)

        navigator.detach(newerHost)

        verify(newerRoot, times(1)).removeOnDestinationChangedListener(any())
        assertTrue(navigator.getCurrentBackStackEntryFlow().toList().isEmpty())
        verify(access).revokeAll()
    }

    @Test fun `a host that never attached leaving changes nothing but access`() {
        whenever(newerRoot.currentBackStackEntryFlow).thenReturn(newerStack)
        val navigator = Navigator(access)
        navigator.attach(newerRoot, newerHost)

        navigator.detach(olderHost)

        assertSame(newerStack, navigator.getCurrentBackStackEntryFlow())
        verify(newerRoot, never()).removeOnDestinationChangedListener(any())
    }
}
