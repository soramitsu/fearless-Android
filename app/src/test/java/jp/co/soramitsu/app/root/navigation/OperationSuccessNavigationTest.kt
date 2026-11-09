package jp.co.soramitsu.app.root.navigation

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.app.R
import jp.co.soramitsu.success.presentation.SuccessFragment
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class OperationSuccessNavigationTest {

    @Before
    fun stubBundle() {
        mockkObject(SuccessFragment.Companion)
        every { SuccessFragment.getBundle(any(), any(), any(), any()) } returns mockk()
    }

    @After
    fun removeBundleStub() {
        unmockkObject(SuccessFragment.Companion)
    }

    private fun entry(destinationId: Int): NavBackStackEntry {
        val entryDestination = mock<NavDestination> { on { id } doReturn destinationId }
        return mock { on { destination } doReturn entryDestination }
    }

    private fun controller(vararg stack: Int): NavController {
        val entries = stack.map(::entry)
        val top = entries.last().destination
        return mock {
            on { currentBackStack } doReturn MutableStateFlow(entries)
            on { currentDestination } doReturn top
        }
    }

    private fun navigator(root: NavController, tabs: NavController) = Navigator(mock()).apply {
        attach(root, mock<AppCompatActivity>())
        attachMainTabs(tabs)
    }

    @Test
    fun `a signed request's preview in the tab stack is gone before the success sheet opens on the root`() {
        val root = controller(R.id.mainFragment, R.id.sessionRequestFragment)
        val tabs = controller(R.id.profileFragment, R.id.requestPreviewFragment)

        navigator(root, tabs).openOperationSuccessAndPopUpToNearestRelatedScreen(null, null, "text", "title")

        inOrder(tabs, root) {
            verify(tabs).popBackStack(R.id.requestPreviewFragment, true)
            verify(root).navigate(eq(R.id.successSheetFragment), anyOrNull(), argThat { popUpToId == R.id.mainFragment })
        }
        verify(tabs, never()).navigate(any<Int>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `with the tab stack in front the sheet opens there and the preview is still removed`() {
        val root = controller(R.id.mainFragment)
        val tabs = controller(R.id.profileFragment, R.id.requestPreviewFragment)

        navigator(root, tabs).openOperationSuccessAndPopUpToNearestRelatedScreen(null, null, "text", "title")

        inOrder(tabs) {
            verify(tabs).popBackStack(R.id.requestPreviewFragment, true)
            verify(tabs).navigate(eq(R.id.successSheetFragment), anyOrNull(), anyOrNull())
        }
        verify(root, never()).navigate(any<Int>(), anyOrNull(), anyOrNull())
    }
}
