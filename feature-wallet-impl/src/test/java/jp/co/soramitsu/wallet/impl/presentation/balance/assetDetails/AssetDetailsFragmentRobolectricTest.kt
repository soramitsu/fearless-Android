package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** A restored legacy asset-details destination forwards to the exact asset once it resumes. */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class AssetDetailsFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val router = mockk<WalletRouter>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
        unmockkObject(FwRestyle)
    }

    @Test
    fun resumingForwardsTheExactAssetOnceAndBackStaysIdle() {
        val payload = AssetPayload("polkadot", "dot")
        host = hostDetails(payload)

        verify(exactly = 1) { router.openAssetDetailsAndPopUpToBalancesList(payload) }
        compose.onNodeWithContentDescription(string(R.string.ux_back)).performClick()
        settle()

        verify(exactly = 0) { router.back() }
    }

    @Test
    fun aRestoredDestinationWithoutAnAssetGoesBack() {
        host = hostDetails(AssetPayload(" ", ""))

        verify(exactly = 1) { router.back() }
        verify(exactly = 0) { router.openAssetDetailsAndPopUpToBalancesList(any()) }
    }

    @Test
    fun legacyPaletteDrawsThePageImageBehindTheTransitionalFrame() {
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        every { FwRestyle.isEnabled(any()) } returns false
        val payload = AssetPayload("kusama", "ksm")

        host = hostDetails(payload)

        compose.onNodeWithContentDescription(string(R.string.ux_back)).assertExists()
        verify(exactly = 1) { router.openAssetDetailsAndPopUpToBalancesList(payload) }
    }

    @Test
    fun bundleCarriesTheAsset() {
        val payload = AssetPayload("moonbeam", "usdt")

        val stored = AssetDetailsFragment.getBundle(payload).get(AssetDetailsFragment.KEY_ASSET_PAYLOAD)

        assertEquals(payload, stored)
    }

    private fun hostDetails(payload: AssetPayload): ActivityController<StubHiltActivity> {
        val viewModel = AssetDetailsViewModel(router, SavedStateHandle(mapOf(AssetDetailsFragment.KEY_ASSET_PAYLOAD to payload)))
        val fragment = AssetDetailsFragment()
        fragment.arguments = AssetDetailsFragment.getBundle(payload)
        val controller = HiltFragmentHost.host(fragment, mapOf(AssetDetailsViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
