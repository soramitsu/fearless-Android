package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.fwPageBackground
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the one-list wallet home with Robolectric's native graphics and keeps each frame as a PNG
 * (`build/one-list-screens/`, or `ONE_LIST_SCREENSHOT_DIR`) for a person to look at. Test values only.
 * Each frame must hold a real picture, and at 200 % text the search stays at least 48 dp tall.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WalletOneListScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val callback = RecordingWalletCallback()

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun homeAsItOpens() = shoot("01-home-top", OneListFixtures.home())

    @Test
    fun homeScrolledToTheList() = shoot("02-home-list", OneListFixtures.home(), scrollToSearch = true)

    @Test
    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    fun homeWholePage() = shoot("03-home-whole-page", OneListFixtures.home())

    @Test
    fun searchForAToken() = shoot("04-search-usdt", OneListFixtures.home(searchQuery = "usdt", expandedGroups = setOf("usdt")), scrollToSearch = true)

    @Test
    fun searchListsAZeroBalance() = shoot("05-search-ksm-zero", OneListFixtures.home(searchQuery = "ksm"), scrollToSearch = true)

    @Test
    fun aNetworkChip() = shoot("06-chip-moonbeam", OneListFixtures.home(selectedNetwork = "moonbeam"), scrollToSearch = true)

    @Test
    fun noMatchOnOneNetwork() = shoot("07-no-match-on-moonbeam", OneListFixtures.noMatch(selectedNetwork = "moonbeam"), scrollToSearch = true)

    @Test
    fun aGroupChip() = shoot(
        "08-popular-group-chip",
        OneListFixtures.home(networkGroup = ChainSelectorViewStateWithFilters.Filter.Popular),
        scrollToSearch = true
    )

    @Test
    fun theOneListNote() = shoot("09-one-list-note", OneListFixtures.home(showOneListNotice = true), scrollToSearch = true)

    @Test
    fun theLastChipsAndMoreNetworks() {
        shoot("12-chips-end-more-networks", OneListFixtures.home(), scrollToSearch = true) {
            compose.revealChip(MORE_TAG)
        }
        val more = compose.onNodeWithTag(MORE_TAG).getUnclippedBoundsInRoot()
        assertTrue("More networks is a readable pill: $more", more.right - more.left >= 120.dp)
    }

    @Test
    fun aSelectedNetworkOutOfSightIsScrolledIntoTheRow() {
        shoot("13-selected-chip-scrolled-into-sight", OneListFixtures.home(selectedNetwork = "assethub"), scrollToSearch = true)
        val chip = compose.onNodeWithTag(networkTag("assethub")).getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("chip $chip in $root", chip.left >= root.left && chip.right <= root.right)
    }

    @Test
    fun largeText() {
        shoot("10-text-200", OneListFixtures.home(), scrollToSearch = true, fontScale = 2f)
        val bounds = compose.onNodeWithTag(SEARCH_TAG).getUnclippedBoundsInRoot()
        assertTrue("search height ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top >= 48.dp)
    }

    @Test
    fun legacyPalette() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false
        every { FwRestyle.paletteEnabled } returns false
        shoot("11-legacy-palette", OneListFixtures.home())
    }

    private fun shoot(
        name: String,
        state: WalletState,
        scrollToSearch: Boolean = false,
        fontScale: Float = 1f,
        before: () -> Unit = {}
    ) {
        // The test activity's title bar is not part of the app.
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FearlessAppTheme {
                    Box(Modifier.fillMaxSize().fwPageBackground()) {
                        WalletScreenWithRefresh(data = state, callback = callback)
                    }
                }
            }
        }
        settle()
        // The wallet list keeps its scroll position for the whole process (rememberForeverLazyListState): start at the top.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        settle()
        if (scrollToSearch) {
            // The page scrolls as one: bring the search to the top, as a user reading the list sees it.
            val list = compose.onNode(hasScrollToIndexAction()).getUnclippedBoundsInRoot().top
            val top = compose.onNodeWithTag(SEARCH_TAG).getUnclippedBoundsInRoot().top
            val pixels = with(compose.density) { (top - list - SCROLL_MARGIN).toPx() }
            compose.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
            settle()
        }
        before()
        settle()
        val bitmap = capture()
        val dir = File(System.getenv("ONE_LIST_SCREENSHOT_DIR") ?: "build/one-list-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name is a picture, not a blank frame", distinctColours(bitmap) > MIN_COLOURS)
    }

    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
        }
    }

    // Compose's own capture first; the activity's view drawn into a bitmap if this Robolectric cannot copy pixels.
    private fun capture(): Bitmap = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }.getOrElse {
        val view = compose.activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun distinctColours(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        for (y in 0 until bitmap.height step SAMPLE_STEP) for (x in 0 until bitmap.width step SAMPLE_STEP) seen += bitmap.getPixel(x, y)
        return seen.size
    }

    private companion object {
        const val SAMPLE_STEP = 7
        const val MIN_COLOURS = 50
        val SCROLL_MARGIN = 72.dp
    }
}
