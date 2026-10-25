package jp.co.soramitsu.app.root.navigation

import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.util.IdentityHashMap

class SavedEndpointUnlockNavigationTest {
    private val root = mock<NavController>()
    private val host = mock<AppCompatActivity>()
    private val hostLifecycle = mock<Lifecycle>()
    private val access = mock<SavedEndpointAccess>()
    private val owner = Any()
    private val health = MutableStateFlow<PinHealth?>(PinHealth.HEALTHY)
    private var hostState = Lifecycle.State.RESUMED
    private var pinState = Lifecycle.State.RESUMED
    private lateinit var origin: NavBackStackEntry
    private lateinit var pin: NavBackStackEntry
    private var current: NavBackStackEntry? = null
    private var previous: NavBackStackEntry? = null
    private lateinit var navigator: Navigator
    private lateinit var bundles: MockedConstruction<Bundle>
    private val values = IdentityHashMap<Bundle, MutableMap<String, Any?>>()
    private var pinArguments: Bundle? = null

    @Before fun setup() {
        bundles = Mockito.mockConstruction(Bundle::class.java) { bundle, _ ->
            val content = mutableMapOf<String, Any?>()
            values[bundle] = content
            doAnswer { content[it.getArgument(0)] = it.getArgument<String?>(1); null }
                .whenever(bundle).putString(any(), anyOrNull())
            doAnswer { content[it.getArgument(0)] = it.getArgument<Parcelable?>(1); null }
                .whenever(bundle).putParcelable(any(), anyOrNull())
            whenever(bundle.getString(any())).thenAnswer { content[it.getArgument(0)] as String? }
        }
        mockkObject(WalletPinHealth)
        every { WalletPinHealth.health } returns health
        WalletViewOnlyAccess.pinResetCompleted()
        whenever(host.lifecycle).thenReturn(hostLifecycle)
        whenever(hostLifecycle.currentState).thenAnswer { hostState }
        origin = entry(R.id.savedEndpointsFragment) { Lifecycle.State.RESUMED }
        pin = entry(R.id.pincodeFragment) { pinState }
        current = origin
        whenever(root.currentBackStackEntry).thenAnswer { current }
        whenever(root.previousBackStackEntry).thenAnswer { previous }
        doAnswer {
            pinArguments = it.getArgument(1)
            previous = origin; current = pin
            null
        }.whenever(root).navigate(eq(R.id.open_pincode_check), any<Bundle>())
        whenever(root.popBackStack()).thenAnswer { current = previous; previous = null; true }
        whenever(access.approve(owner, "request-a")).thenReturn(true)
        navigator = Navigator(access).apply { attach(root, host) }
    }

    @After fun teardown() {
        bundles.close()
        unmockkObject(WalletPinHealth)
        WalletViewOnlyAccess.pinResetCompleted()
    }

    @Test fun rootEntryOnlyOpensLockedDestinationAndRevokesAnyEarlierAuthority() {
        navigator.openSavedEndpoints()
        verify(access).revokeAll()
        verify(root).navigate(R.id.savedEndpointsFragment)
        verify(access, never()).approve(any(), any())
    }

    @Test fun exactCurrentPinCompletionApprovesOnceBeforeReturningToOriginalEntry() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        val action = action()
        assertTrue(action.toolbarConfiguration.backVisible)
        assertEquals(R.string.saved_endpoints_unlock, action.toolbarConfiguration.titleRes)
        navigator.openAfterPinCode(checkNotNull(action.delayedNavigation))
        inOrder(access, root) {
            verify(access).approve(owner, "request-a")
            verify(root).popBackStack()
        }
        assertSame(origin, current)
        navigator.openAfterPinCode(checkNotNull(action.delayedNavigation))
        verify(access, times(1)).approve(owner, "request-a")
        verify(root, times(1)).popBackStack()
    }

    @Test fun staleDifferentTokenCannotConsumeTheCurrentAttemptOrPopItsPinScreen() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        val old = Bundle().apply { putString("saved_endpoint_unlock_request", "stale") }
        navigator.openAfterPinCode(NavComponentDelayedNavigation(R.id.savedEndpointsFragment, old))
        verify(access, never()).approve(any(), any())
        verify(root, never()).popBackStack()
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access).approve(owner, "request-a")
    }

    @Test fun sameIdDifferentOriginOrPinEntryCannotApproveOrPop() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        previous = entry(R.id.savedEndpointsFragment) { Lifecycle.State.RESUMED }
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access, never()).approve(any(), any())
        verify(root, never()).popBackStack()
        verify(access).revoke(owner)
    }

    @Test fun nonResumedHostOrPinCannotApprove() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        hostState = Lifecycle.State.STARTED
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access, never()).approve(any(), any())
        verify(root, never()).popBackStack()
    }

    @Test fun viewOnlyAndUnhealthyPinAreDeniedEvenWithCorrectNavigationToken() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        WalletViewOnlyAccess.enter()
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access, never()).approve(any(), any())
        assertSame(origin, current)
        WalletViewOnlyAccess.pinResetCompleted()
        health.value = PinHealth.UNREADABLE
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access, never()).approve(any(), any())
        assertSame(origin, current)
    }

    @Test fun revokedHostRoundTripReturnsToLockedPageAndNeverManufacturesApproval() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        navigator.revokeSavedEndpointAccess()
        whenever(access.approve(owner, "request-a")).thenReturn(false)
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        inOrder(access) { verify(access).revokeAll(); verify(access).approve(owner, "request-a"); verify(access).revoke(owner) }
        assertSame(origin, current)
    }

    @Test fun failedPopRevokesAnApprovedAttempt() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        doReturn(false).whenever(root).popBackStack()
        assertSame(pin, current)
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        inOrder(access, root) {
            verify(access).approve(owner, "request-a")
            verify(root).popBackStack()
            verify(access).revoke(owner)
        }
    }

    @Test fun throwingPopRevokesAnApprovedAttemptAndCannotReplayItsCompletion() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        val delayed = checkNotNull(action().delayedNavigation)
        val failure = IllegalStateException("synthetic pop failure")
        doThrow(failure).whenever(root).popBackStack()
        assertSame(failure, assertThrows(IllegalStateException::class.java) { navigator.openAfterPinCode(delayed) })
        inOrder(access, root) {
            verify(access).approve(owner, "request-a")
            verify(root).popBackStack()
            verify(access).revoke(owner)
        }
        assertSame(pin, current)
        navigator.openAfterPinCode(delayed)
        verify(access, times(1)).approve(owner, "request-a")
        verify(access, times(1)).revoke(owner)
        verify(root, times(1)).popBackStack()
    }

    @Test fun cancelledPopRevokesAnApprovedAttemptAndPreservesCancellation() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        val failure = kotlinx.coroutines.CancellationException("synthetic pop cancellation")
        doThrow(failure).whenever(root).popBackStack()
        assertSame(failure, assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        })
        inOrder(access, root) {
            verify(access).approve(owner, "request-a")
            verify(root).popBackStack()
            verify(access).revoke(owner)
        }
        assertSame(pin, current)
    }

    @Test fun detachedOrRestoredNavigatorCannotApproveTheOldRequest() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        val delayed = checkNotNull(action().delayedNavigation)
        navigator.detach(); navigator.attach(root, host)
        navigator.openAfterPinCode(delayed)
        Navigator(access).apply { attach(root, host) }.openAfterPinCode(delayed)
        verify(access).revokeAll()
        verify(access, never()).approve(any(), any())
        verify(root, never()).popBackStack()
    }

    @Test fun sameDestinationIdOnAnotherPinEntryCannotApproveOrPopTheForeignEntry() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        val foreign = entry(R.id.pincodeFragment) { Lifecycle.State.RESUMED }
        current = foreign
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access, never()).approve(any(), any())
        verify(root, never()).popBackStack()
        verify(access).revoke(owner)
        assertSame(foreign, current)
    }

    @Test fun nonResumedPinCannotApproveEvenWhileHostAndOriginAreCurrent() {
        assertTrue(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        pinState = Lifecycle.State.STARTED
        navigator.openAfterPinCode(checkNotNull(action().delayedNavigation))
        verify(access, never()).approve(any(), any())
        verify(access).revoke(owner)
        verify(root).popBackStack()
        assertSame(origin, current)
    }

    @Test fun nonResumedSavedEntryCannotStartAnotherPinRoundTrip() {
        current = entry(R.id.savedEndpointsFragment) { Lifecycle.State.STARTED }
        assertFalse(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        verify(root, never()).navigate(eq(R.id.open_pincode_check), any<Bundle>())
        verify(access, never()).approve(any(), any())
    }

    @Test fun missingOrWrongOrBackgroundOriginDoesNotStartPinAndNavigationFailureIsLocal() {
        current = null
        assertFalse(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        current = entry(R.id.mainFragment) { Lifecycle.State.RESUMED }
        assertFalse(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        current = origin; hostState = Lifecycle.State.STARTED
        assertFalse(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        verify(root, never()).navigate(eq(R.id.open_pincode_check), any<Bundle>())
        hostState = Lifecycle.State.RESUMED
        doThrow(IllegalStateException("unavailable")).whenever(root).navigate(eq(R.id.open_pincode_check), any<Bundle>())
        assertFalse(navigator.requestSavedEndpointUnlock(owner, "request-a"))
        verify(access).revoke(owner)
    }

    private fun action(): PinCodeAction.Check = values.getValue(checkNotNull(pinArguments))
        .getValue(PincodeFragment.KEY_PINCODE_ACTION) as PinCodeAction.Check

    private fun entry(id: Int, state: () -> Lifecycle.State): NavBackStackEntry {
        val destination = mock<NavDestination>()
        whenever(destination.id).thenReturn(id)
        val lifecycle = mock<Lifecycle>()
        whenever(lifecycle.currentState).thenAnswer { state() }
        return mock<NavBackStackEntry>().also {
            whenever(it.destination).thenReturn(destination)
            whenever(it.lifecycle).thenReturn(lifecycle)
        }
    }
}
