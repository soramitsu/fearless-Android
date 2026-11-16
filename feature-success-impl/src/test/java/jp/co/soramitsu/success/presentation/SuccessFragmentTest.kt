package jp.co.soramitsu.success.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.feedback.TransferMoments
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.success.FragmentHiltShim
import jp.co.soramitsu.success.ShimApplication
import jp.co.soramitsu.success.ShimHostActivity
import jp.co.soramitsu.success.idleMainUntil
import jp.co.soramitsu.success.launchHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = ShimApplication::class)
class SuccessFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val explorer = Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic"), "https://scan.test/{type}/{value}")
    private val chain = mockk<Chain> {
        every { explorers } returns listOf(explorer)
        every { ecosystem } returns Ecosystem.Substrate
        every { isEthereumChain } returns false
    }
    private val chainRegistry = mockk<ChainRegistry> { coEvery { getChain(CHAIN_ID) } returns chain }
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "s:${firstArg<Int>()}" } }
    private lateinit var host: ShimHostActivity
    private lateinit var fragment: SuccessFragment

    @Before fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        FragmentHiltShim.viewModel(SuccessViewModel::class) { handle ->
            SuccessViewModel(mockk(relaxed = true), chainRegistry, handle, resources, mockk(relaxed = true), mockk(relaxed = true))
        }
        host = launchHost()
    }

    @After fun tearDown() {
        FragmentHiltShim.reset()
        TransferSeal.clear()
    }

    private fun show() {
        fragment = SuccessFragment().apply { arguments = SuccessFragment.getBundle(HASH, CHAIN_ID, "Accepted by SORA", "Swap sent") }
        fragment.show(host.supportFragmentManager, "success")
        idleMainUntil { fragment.dialog?.isShowing == true }
    }

    @Test fun sheetOpensExpandedWithTheOperationTexts() {
        show()

        val behavior = (fragment.requireDialog() as BottomSheetDialog).behavior
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
        assertTrue(behavior.isHideable)
        assertTrue(behavior.skipCollapsed)
        compose.onNodeWithText("Swap sent").assertExists()
        compose.onNodeWithText("Accepted by SORA").assertExists()
        compose.onNodeWithTag("success_solar_ignite").assertDoesNotExist()
    }

    @Test fun shareOffersTheExplorerLinkThroughTheChooser() {
        show()

        compose.onNodeWithText(context.getString(R.string.common_share)).performClick()
        idleMainUntil { shadowOf(host).peekNextStartedActivityForResult() != null }

        val started = shadowOf(host).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CHOOSER, started.intent.action)
        @Suppress("DEPRECATION")
        val shared = started.intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("https://scan.test/extrinsic/$HASH", shared.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun explorerButtonOpensTheBrowser() {
        show()

        compose.onNodeWithText(Chain.Explorer.Type.SUBSCAN.capitalizedName).performClick()
        idleMainUntil { shadowOf(host).peekNextStartedActivity() != null }

        assertEquals("https://scan.test/extrinsic/$HASH", shadowOf(host).nextStartedActivity.dataString)
    }

    @Test fun aJustSentTransferIgnitesTheMark() {
        TransferMoments.markSent(System.nanoTime() / 1_000_000)

        show()

        compose.onNodeWithTag("success_solar_ignite").assertExists()
    }

    private companion object {
        const val CHAIN_ID = "chain-id"
        const val HASH = "0x123456789abcdef0"
    }
}
