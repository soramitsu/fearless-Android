package jp.co.soramitsu.wallet.impl.presentation.balance.nft.filters

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The NFT filter sheet shows the stored filters, saves a switched one and closes itself. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class NftFiltersFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val interactor = mockk<NFTInteractor>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var fragment: NftFiltersFragment

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { interactor.nftFiltersFlow() } returns flowOf(mapOf(NFTFilter.AirDrops to true))
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun switchingAFilterOffSavesIt() {
        host = hostSheet()

        compose.onNodeWithText("Hide NFTs").assertExists()
        compose.onNodeWithText(NFTFilter.AirDrops.name).assertExists()
        compose.onNode(isToggleable()).performClick()
        settle()

        verify { interactor.setNFTFilter(NFTFilter.AirDrops, false) }
    }

    @Test
    fun sheetCannotBeDraggedAndItsCloseButtonDismissesIt() {
        host = hostSheet()

        assertFalse((fragment.dialog as BottomSheetDialog).behavior.isDraggable)
        compose.onNode(hasClickAction() and !isToggleable()).performClick()
        settle()

        assertFalse(fragment.isAdded)
        verify(exactly = 0) { interactor.setNFTFilter(any(), any()) }
    }

    private fun hostSheet(): ActivityController<StubHiltActivity> {
        fragment = NftFiltersFragment()
        val controller = HiltFragmentHost.host(fragment, emptyMap(), inject = { it.nftInteractor = interactor })
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
