package jp.co.soramitsu.app.root.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.FloatingWindow
import androidx.navigation.NavAction
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavOptions
import androidx.navigation.NavigatorProvider
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.create_backup_password.CreateBackupPasswordDialog
import jp.co.soramitsu.account.impl.presentation.nomis_scoring.ScoreDetailsFragment
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.success.presentation.SuccessFragment
import jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails.TonConnectionDetailsFragment
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignRequestFragment
import jp.co.soramitsu.walletconnect.impl.presentation.chainschooser.ChainChooseFragment
import jp.co.soramitsu.walletconnect.impl.presentation.requestpreview.RequestPreviewFragment
import jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal.SessionProposalFragment
import jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest.WalletConnectSignMessageFragment
import jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata.RawDataFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.w3c.dom.Element

/**
 * A sheet opened over a root sheet must be the one Back and results reach. Each test follows the stacks
 * the user would see; [FakeStack] keeps a real list behind the mocked controller, with the real graph
 * lookups the routes use.
 */
class DappSheetNavigationTest {

    private class Sheet(destinationId: Int) : NavDestination("dialog"), FloatingWindow {
        init {
            id = destinationId
        }
    }

    private class FakeStack(private val navGraph: NavGraph, vararg destinationIds: Int) {
        private val entries = mutableListOf<NavBackStackEntry>()
        private val currentEntry = MutableSharedFlow<NavBackStackEntry>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        val ids: List<Int> get() = entries.map { it.destination.id }

        val controller: NavController = mock {
            on { graph } doReturn navGraph
            on { currentDestination } doAnswer { entries.lastOrNull()?.destination }
            on { currentBackStackEntry } doAnswer { entries.lastOrNull() }
            on { previousBackStackEntry } doAnswer { entries.getOrNull(entries.lastIndex - 1) }
            on { currentBackStack } doAnswer { MutableStateFlow(entries.toList()) }
            on { currentBackStackEntryFlow } doReturn currentEntry
            on { popBackStack() } doAnswer { pop() }
            on { popBackStack(any<Int>(), any<Boolean>()) } doAnswer {
                popTo(it.getArgument(0), it.getArgument(1))
            }
            on { getBackStackEntry(any<Int>()) } doAnswer { entry(it.getArgument(0)) }
        }

        init {
            doAnswer { navigate(it.getArgument(0), null) }
                .whenever(controller).navigate(any<Int>(), anyOrNull<Bundle>())
            doAnswer { navigate(it.getArgument(0), it.getArgument(2)) }
                .whenever(controller).navigate(any<Int>(), anyOrNull<Bundle>(), anyOrNull<NavOptions>())
            destinationIds.forEach(::push)
        }

        fun entry(destinationId: Int): NavBackStackEntry =
            requireNotNull(entries.lastOrNull { it.destination.id == destinationId }) {
                "No destination with ID $destinationId is on the back stack"
            }

        /** What system Back and a tap outside the sheet do: the dialog dismisses, its own entry pops. */
        fun dismissTopSheet() {
            pop()
        }

        private fun navigate(destinationId: Int, options: NavOptions?) {
            val current = requireNotNull(entries.lastOrNull()).destination
            val resolvedId = current.getAction(destinationId)?.destinationId ?: destinationId
            val currentGraph = (current as? NavGraph) ?: requireNotNull(current.parent)
            // AndroidX resolves the destination before popUpTo; it searches parents, not sibling graph children.
            val node = requireNotNull(currentGraph.findNode(resolvedId)) { "Unreachable destination $destinationId" }
            options?.let { popTo(it.popUpToId, it.isPopUpToInclusive()) }
            push(node)
        }

        private fun push(destinationId: Int) {
            push(requireNotNull(navGraph.findRecursively(destinationId)) { "Unknown destination $destinationId" })
        }

        private fun push(node: NavDestination) {
            val entryHandle = SavedStateHandle()
            entries += mock<NavBackStackEntry> {
                on { destination } doReturn node
                on { savedStateHandle } doReturn entryHandle
            }
            currentEntry.tryEmit(entries.last())
        }

        private fun pop(): Boolean {
            if (entries.isEmpty()) return false
            entries.removeAt(entries.lastIndex)
            entries.lastOrNull()?.let(currentEntry::tryEmit)
            return true
        }

        private fun popTo(destinationId: Int, inclusive: Boolean): Boolean {
            if (destinationId !in ids) return false
            while (ids.last() != destinationId) pop()
            if (inclusive) pop()
            return true
        }

        private fun NavGraph.findRecursively(destinationId: Int): NavDestination? {
            findNode(destinationId)?.let { return it }
            return firstNotNullOfOrNull { (it as? NavGraph)?.findRecursively(destinationId) }
        }
    }

    private fun graph(graphId: Int, startId: Int, vararg children: NavDestination) =
        NavGraph(NavGraphNavigator(NavigatorProvider())).apply {
            id = graphId
            addDestinations(*children)
            setStartDestination(startId)
        }

    private fun page(destinationId: Int) = NavDestination("fragment").apply { id = destinationId }

    /** The parts of main_nav_graph these routes use. */
    private fun rootGraph() = graph(
        R.id.root_nav_graph,
        R.id.main_nav_graph,
        graph(
            R.id.main_nav_graph,
            R.id.mainFragment,
            page(R.id.mainFragment),
            page(R.id.aboutFragment),
            page(R.id.scoreDetailsFragment),
            Sheet(R.id.sessionProposalFragment),
            Sheet(R.id.sessionRequestFragment),
            Sheet(R.id.requestPreviewFragment),
            Sheet(R.id.rawDataFragment),
            Sheet(R.id.chainChooseFragment),
            Sheet(R.id.successSheetFragment),
            Sheet(R.id.tonSignRequestFragment),
            Sheet(R.id.tonConnectionDetails),
            Sheet(R.id.createBackupPasswordDialog)
        ),
        page(R.id.pincodeFragment)
    ).apply { putAction(R.id.open_pincode_check, NavAction(R.id.pincodeFragment)) }

    /** The parts of bottom_nav_graph these routes use. */
    private fun tabGraph() = graph(
        R.id.bottom_nav_graph,
        R.id.settingsGraph,
        graph(
            R.id.settingsGraph,
            R.id.profileFragment,
            page(R.id.profileFragment),
            page(R.id.aboutFragment),
            Sheet(R.id.requestPreviewFragment),
            Sheet(R.id.rawDataFragment),
            Sheet(R.id.chainChooseFragment),
            Sheet(R.id.tonConnectionDetails)
        ),
        graph(R.id.portfolioGraph, R.id.walletFragment, page(R.id.walletFragment)),
        Sheet(R.id.scoreDetailsFragment),
        Sheet(R.id.successSheetFragment)
    )

    private val root = FakeStack(rootGraph(), R.id.mainFragment)
    private val tabs = FakeStack(tabGraph(), R.id.profileFragment)
    private val navigator = Navigator(mock()).apply {
        attach(root.controller, mock<AppCompatActivity>())
        attachMainTabs(tabs.controller)
    }

    private var pinAction: PinCodeAction? = null

    private val bundleOwners = listOf(
        TonSignRequestFragment.Companion,
        TonConnectionDetailsFragment.Companion,
        CreateBackupPasswordDialog.Companion,
        PincodeFragment.Companion,
        RequestPreviewFragment.Companion,
        RawDataFragment.Companion,
        ChainChooseFragment.Companion,
        SessionProposalFragment.Companion,
        WalletConnectSignMessageFragment.Companion,
        SuccessFragment.Companion,
        ScoreDetailsFragment.Companion
    )

    @Before
    fun stubBundles() {
        WalletViewOnlyAccess.pinResetCompleted()
        WalletSessionLock.unlock()
        bundleOwners.forEach { mockkObject(it) }
        every { RequestPreviewFragment.getBundle(any(), any()) } returns mockk()
        every { RawDataFragment.getBundle(any()) } returns mockk()
        every { ChainChooseFragment.getBundle(any()) } returns mockk()
        every { SessionProposalFragment.getBundle(any()) } returns mockk()
        every { WalletConnectSignMessageFragment.getBundle(any()) } returns mockk()
        every { SuccessFragment.getBundle(any(), any(), any(), any()) } returns mockk()
        every { ScoreDetailsFragment.getBundle(any()) } returns mockk()
        every { TonSignRequestFragment.getBundle(any(), any(), any()) } returns mockk()
        every { TonConnectionDetailsFragment.getBundle(any(), any()) } returns mockk()
        every { CreateBackupPasswordDialog.getBundle(any()) } returns mockk()
        every { PincodeFragment.getPinCodeBundle(any()) } answers {
            pinAction = firstArg()
            mockk()
        }
    }

    @After
    fun removeStubs() {
        bundleOwners.forEach { unmockkObject(it) }
        WalletSessionLock.lock()
    }

    private fun assertStacks(rootIds: List<Int>, tabIds: List<Int> = listOf(R.id.profileFragment)) {
        assertEquals(rootIds, root.ids)
        assertEquals(tabIds, tabs.ids)
    }

    private fun openRequestSheet() {
        navigator.openWalletConnectSessionRequest("topic")
        assertStacks(listOf(R.id.mainFragment, R.id.sessionRequestFragment))
    }

    private fun openProposalSheet() {
        navigator.openWalletConnectSessionProposal("pairing")
        assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment))
    }

    @Test
    fun `a preview opened from a request joins the root's stack and leaves the tabs alone`() {
        openRequestSheet()

        navigator.openRequestPreview("topic", 9)

        assertStacks(listOf(R.id.mainFragment, R.id.sessionRequestFragment, R.id.requestPreviewFragment))
    }

    @Test
    fun `Back on the preview closes the preview and keeps the request`() {
        openRequestSheet()
        navigator.openRequestPreview("topic", 9)

        navigator.back()

        assertStacks(listOf(R.id.mainFragment, R.id.sessionRequestFragment))
    }

    @Test
    fun `closing raw data returns to the preview, then to the request, then to the wallet`() {
        openRequestSheet()
        navigator.openRequestPreview("topic", 9)
        navigator.openRawData("payload")
        assertStacks(
            listOf(R.id.mainFragment, R.id.sessionRequestFragment, R.id.requestPreviewFragment, R.id.rawDataFragment)
        )

        navigator.back()
        assertStacks(listOf(R.id.mainFragment, R.id.sessionRequestFragment, R.id.requestPreviewFragment))
        navigator.back()
        assertStacks(listOf(R.id.mainFragment, R.id.sessionRequestFragment))
        navigator.back()
        assertStacks(listOf(R.id.mainFragment))
    }

    @Test
    fun `signing from the preview leaves the flow and closing the success sheet shows the wallet`() {
        openRequestSheet()
        navigator.openRequestPreview("topic", 9)

        navigator.openOperationSuccessAndPopUpToNearestRelatedScreen(null, null, "text", "title")
        assertStacks(listOf(R.id.mainFragment, R.id.successSheetFragment))

        navigator.back()
        assertStacks(listOf(R.id.mainFragment))
    }

    @Test
    fun `Done in the chooser closes only the chooser and the proposal gets the selection`() = runTest {
        openProposalSheet()
        val received = mutableListOf<ChainChooseResult>()
        val selection = ChainChooseResult(setOf("eip155:137"))

        val collecting = launch {
            navigator.openSelectMultipleChainsForResult(listOf("eip155:137", "eip155:56"), emptyList())
                .collect { received += it }
        }
        runCurrent()
        assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment, R.id.chainChooseFragment))

        navigator.backWithResult(ChainChooseFragment.RESULT to selection)
        runCurrent()

        assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment))
        assertEquals(listOf(selection), received)
        val proposalHandle = root.entry(R.id.sessionProposalFragment).savedStateHandle
        assertNull("A delivered result must not stay for the next chooser", proposalHandle[ChainChooseFragment.RESULT])
        assertFalse(collecting.isActive)
    }

    @Test
    fun `closing the chooser without Done changes nothing, even after the view-only list reported Done`() =
        runTest {
            openProposalSheet()
            navigator.openSelectMultipleChains(listOf("eip155:1"), listOf("eip155:1"), isViewMode = true)
            assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment, R.id.chainChooseFragment))
            navigator.backWithResult(ChainChooseFragment.RESULT to ChainChooseResult(setOf("eip155:1")))
            assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment))

            val received = mutableListOf<ChainChooseResult>()
            val collecting = launch {
                navigator.openSelectMultipleChainsForResult(listOf("eip155:137"), emptyList())
                    .collect { received += it }
            }
            runCurrent()
            root.dismissTopSheet()
            runCurrent()

            assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment))
            assertEquals(emptyList<ChainChooseResult>(), received)
            assertFalse(collecting.isActive)
        }

    @Test
    fun `a dApp request opening over the chooser does not end the chooser's result`() = runTest {
        openProposalSheet()
        val received = mutableListOf<ChainChooseResult>()
        val selection = ChainChooseResult(setOf("eip155:56"))
        val collecting = launch {
            navigator.openSelectMultipleChainsForResult(listOf("eip155:56"), emptyList())
                .collect { received += it }
        }
        runCurrent()

        navigator.openWalletConnectSessionRequest("topic")
        runCurrent()
        assertStacks(
            listOf(R.id.mainFragment, R.id.sessionProposalFragment, R.id.chainChooseFragment, R.id.sessionRequestFragment)
        )
        assertTrue(collecting.isActive)

        navigator.back()
        runCurrent()
        assertTrue(collecting.isActive)
        navigator.backWithResult(ChainChooseFragment.RESULT to selection)
        runCurrent()

        assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment))
        assertEquals(listOf(selection), received)
    }

    @Test
    fun `another request does not open over a request's preview or raw data`() {
        openRequestSheet()
        navigator.openRequestPreview("topic", 9)
        val withPreview = listOf(R.id.mainFragment, R.id.sessionRequestFragment, R.id.requestPreviewFragment)

        navigator.openWalletConnectSessionRequest("topic")
        assertStacks(withPreview)

        navigator.openRawData("payload")
        navigator.openWalletConnectSessionRequest("topic")
        assertStacks(withPreview + R.id.rawDataFragment)
    }

    @Test
    fun `another proposal does not open over the proposal's chooser`() {
        openProposalSheet()
        navigator.openSelectMultipleChains(listOf("eip155:1"), listOf("eip155:1"), isViewMode = true)

        navigator.openWalletConnectSessionProposal("pairing")

        assertStacks(listOf(R.id.mainFragment, R.id.sessionProposalFragment, R.id.chainChooseFragment))
    }

    @Test
    fun `with no root sheet in front the sheets stay in the tab stack and Back closes them there`() =
        runTest {
            navigator.openRawData("payload")
            assertStacks(listOf(R.id.mainFragment), listOf(R.id.profileFragment, R.id.rawDataFragment))
            navigator.back()
            assertStacks(listOf(R.id.mainFragment))

            val received = mutableListOf<ChainChooseResult>()
            val selection = ChainChooseResult(setOf("eip155:137"))
            launch {
                navigator.openSelectMultipleChainsForResult(listOf("eip155:137"), emptyList())
                    .collect { received += it }
            }
            runCurrent()
            assertStacks(listOf(R.id.mainFragment), listOf(R.id.profileFragment, R.id.chainChooseFragment))
            navigator.backWithResult(ChainChooseFragment.RESULT to selection)
            runCurrent()
            assertStacks(listOf(R.id.mainFragment))
            assertEquals(listOf(selection), received)
        }

    @Test
    fun `a full page keeps the tab stack while a root sheet shows`() {
        openRequestSheet()

        navigator.openAboutScreen()

        assertStacks(
            listOf(R.id.mainFragment, R.id.sessionRequestFragment),
            listOf(R.id.profileFragment, R.id.aboutFragment)
        )
    }

    @Test
    fun `a tab sheet that is a full page in the root's graph keeps the tab stack`() {
        openRequestSheet()

        navigator.openScoreDetailsScreen(1)

        assertStacks(
            listOf(R.id.mainFragment, R.id.sessionRequestFragment),
            listOf(R.id.profileFragment, R.id.scoreDetailsFragment)
        )
    }

    @Test
    fun `TON pairing from portfolio keeps that tab and returns the connection from its root dialog`() = runTest {
        val portfolio = FakeStack(tabGraph(), R.id.walletFragment)
        navigator.attachMainTabs(portfolio.controller)
        val received = async { navigator.openTonConnectionAndWaitForResult(mock(), "proof-payload") }
        runCurrent()
        assertEquals(listOf(R.id.mainFragment, R.id.tonConnectionDetails), root.ids)
        assertEquals(listOf(R.id.walletFragment), portfolio.ids)

        navigator.backWithResult(TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY to "{\"event\":\"connect\"}")
        runCurrent()

        assertEquals("connect", received.await().getString("event"))
        assertEquals(listOf(R.id.mainFragment), root.ids)
        assertEquals(listOf(R.id.walletFragment), portfolio.ids)
        assertNull(root.entry(R.id.mainFragment).savedStateHandle[TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY])
        assertNull(portfolio.entry(R.id.walletFragment).savedStateHandle[TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY])
    }

    @Test
    fun `a locked wallet cannot open a TON connection request`() = runTest {
        WalletSessionLock.lock()

        val attempt = async { runCatching { navigator.openTonConnectionAndWaitForResult(mock(), null) } }
        runCurrent()

        assertTrue("A refused request must not wait for a dialog result", attempt.isCompleted)
        assertTrue(attempt.await().exceptionOrNull() is IllegalStateException)
        assertStacks(listOf(R.id.mainFragment))
    }

    @Test
    fun `a TON connection request cannot escape the PIN graph through stale mounted tabs`() = runTest {
        val startId = R.id.pincodeFragment
        val lockedRoot = FakeStack(graph(R.id.root_nav_graph, startId, page(startId)), startId)
        navigator.attach(lockedRoot.controller, mock<AppCompatActivity>())

        val attempt = async { runCatching { navigator.openTonConnectionAndWaitForResult(mock(), null) } }
        runCurrent()

        assertTrue("A refused request must not wait for a dialog result", attempt.isCompleted)
        assertTrue(attempt.await().exceptionOrNull() is IllegalStateException)
        assertEquals(listOf(startId), lockedRoot.ids)
        assertEquals(listOf(R.id.profileFragment), tabs.ids)
    }

    @Test
    fun `TON signing opens its root dialog with tabs mounted and returns its result to the original entry`() = runTest {
        val received = async { navigator.openTonSignRequestWithResult(mock(), "sendTransaction", mock()) }
        runCurrent()
        assertStacks(listOf(R.id.mainFragment, R.id.tonSignRequestFragment))

        val signed = Result.success("request-id" to "signed-boc")
        navigator.backWithResult(TonSignRequestFragment.TON_SIGN_RESULT_KEY to signed)
        runCurrent()

        assertEquals(signed, received.await())
        assertStacks(listOf(R.id.mainFragment))
        assertNull(root.entry(R.id.mainFragment).savedStateHandle[TonSignRequestFragment.TON_SIGN_RESULT_KEY])
        assertNull(tabs.entry(R.id.profileFragment).savedStateHandle[TonSignRequestFragment.TON_SIGN_RESULT_KEY])
    }

    @Test
    fun `a locked wallet cannot open a TON signing dialog even though the root owns it`() = runTest {
        WalletSessionLock.lock()

        val result = navigator.openTonSignRequestWithResult(mock(), "sendTransaction", mock())

        assertTrue(result.isFailure)
        assertStacks(listOf(R.id.mainFragment))
    }

    @Test
    fun `an unreachable TON dialog remains refused while tabs are mounted`() = runTest {
        val startId = R.id.pincodeFragment
        val lockedRoot = FakeStack(graph(R.id.root_nav_graph, startId, page(startId)), startId)
        navigator.attach(lockedRoot.controller, mock<AppCompatActivity>())

        val result = navigator.openTonSignRequestWithResult(mock(), "sendTransaction", mock())

        assertTrue(result.isFailure)
        assertEquals(listOf(startId), lockedRoot.ids)
        assertEquals(listOf(R.id.profileFragment), tabs.ids)
    }

    @Test
    fun `Google backup keeps PIN and password on the root stack and returns its result once`() = runTest {
        val key = CreateBackupPasswordDialog.RESULT_BACKUP_KEY
        val origin = root.entry(R.id.mainFragment).savedStateHandle
        origin[key] = -1
        val received = mutableListOf<Int>()
        val collecting = launch {
            navigator.openCreateBackupPasswordDialogAfterPinCheck(SaveBackupPayload(7L, null), null)
                .collect { received += it }
        }
        runCurrent()
        assertStacks(listOf(R.id.mainFragment, R.id.pincodeFragment))
        assertEquals(emptyList<Int>(), received)
        assertNull(origin[key])
        val check = pinAction as PinCodeAction.Check
        assertFalse(check.allowBiometric)

        navigator.openAfterPinCode(requireNotNull(check.delayedNavigation))
        runCurrent()
        assertStacks(listOf(R.id.mainFragment, R.id.createBackupPasswordDialog))
        navigator.backWithResult(key to 1)
        runCurrent()

        assertStacks(listOf(R.id.mainFragment))
        assertEquals(listOf(1), received)
        assertFalse(collecting.isActive)
        assertNull(origin[key])
        assertNull(tabs.entry(R.id.profileFragment).savedStateHandle[key])
    }

    @Test
    fun `backup completion restores the PIN origin graph before resolving its dialog and ignores a replay`() {
        root.controller.navigate(R.id.open_pincode_check, mock<Bundle>())
        val delayed = NavComponentDelayedNavigation(R.id.createBackupPasswordDialog, mock<Bundle>())
        assertStacks(listOf(R.id.mainFragment, R.id.pincodeFragment))

        navigator.openAfterPinCode(delayed)
        assertStacks(listOf(R.id.mainFragment, R.id.createBackupPasswordDialog))
        navigator.back()
        navigator.openAfterPinCode(delayed)
        assertStacks(listOf(R.id.mainFragment))
    }

    @Test
    fun `backup without a PIN detour uses the same root result owner`() = runTest {
        val key = CreateBackupPasswordDialog.RESULT_BACKUP_KEY
        val received = mutableListOf<Int>()
        launch {
            navigator.openCreateBackupPasswordDialogWithResult(null).collect { received += it }
        }
        runCurrent()
        assertStacks(listOf(R.id.mainFragment, R.id.createBackupPasswordDialog))

        navigator.backWithResult(key to 2)
        runCurrent()

        assertStacks(listOf(R.id.mainFragment))
        assertEquals(listOf(2), received)
        assertNull(root.entry(R.id.mainFragment).savedStateHandle[key])
    }

    @Test
    fun `root-only product dialogs remain explicit root destinations in the production graphs`() {
        val rootDialogs = dialogs("main_nav_graph.xml")
            .filter { it.parentNode === it.ownerDocument.documentElement }
            .map { it.destinationName }.toSet()
        val tabDialogs = dialogs("bottom_nav_graph.xml").map { it.destinationName }.toSet()

        listOf("tonSignRequestFragment", "createBackupPasswordDialog").forEach { name ->
            assertTrue(name in rootDialogs)
            assertFalse(name in tabDialogs)
        }
        assertTrue("tonConnectionDetails" in rootDialogs)
        val tabConnection = dialogs("bottom_nav_graph.xml").single { it.destinationName == "tonConnectionDetails" }
        assertEquals("settingsGraph", (tabConnection.parentNode as Element).destinationName)
    }

    @Test
    fun `every production tab sheet can join the root's stack`() {
        val rootSheets = dialogs("main_nav_graph.xml").map { it.destinationName }.toSet()
        val tabSheets = dialogs("bottom_nav_graph.xml").map { it.destinationName }

        // Opens only from the wallet's own pages; in the root's graph it is a full page.
        val notJoining = tabSheets.filterNot { it in rootSheets }

        assertEquals(listOf("scoreDetailsFragment"), notJoining)
    }

    @Test
    fun `the dApp sheets in production resolve from each other in the root's graph`() {
        val topLevel = dialogs("main_nav_graph.xml")
            .filter { it.parentNode === it.ownerDocument.documentElement }
            .map { it.destinationName }

        listOf(
            "sessionProposalFragment",
            "sessionRequestFragment",
            "requestPreviewFragment",
            "rawDataFragment",
            "chainChooseFragment"
        ).forEach { assertTrue("$it must be a top-level dialog of the root's graph", it in topLevel) }
    }

    private val Element.destinationName: String get() = getAttribute("android:id").substringAfter("/")

    private fun dialogs(graphName: String): List<Element> {
        val projectRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/res/navigation").isDirectory }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(projectRoot, "app/src/main/res/navigation/$graphName"))
        val nodes = document.getElementsByTagName("dialog")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
