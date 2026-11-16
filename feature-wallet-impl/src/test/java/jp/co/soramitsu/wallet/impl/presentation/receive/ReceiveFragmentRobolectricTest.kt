package jp.co.soramitsu.wallet.impl.presentation.receive

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.receive.model.QrSharingPayload
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The receive sheet renders the address QR, forwards its actions and shares the QR image through a chooser. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class ReceiveFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val qrShareEvent = MutableLiveData<Event<QrSharingPayload>>()
    private var fileProviderMocked = false
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var viewModel: ReceiveViewModel

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
        viewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow<LoadingState<ReceiveScreenViewState>>(LoadingState.Loaded(receiveState()))
            every { shareEvent } returns qrShareEvent
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
        if (fileProviderMocked) unmockkStatic(FileProvider::class)
    }

    @Test
    fun sheetRendersTheQrAndForwardsCopyAndShare() {
        val fragment = hostReceive()

        compose.onNodeWithText("Main wallet").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_copy_address)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.ux_share_address)).performClick()
        compose.waitForIdle()

        verify { viewModel.copyClicked() }
        verify { viewModel.shareClicked() }
        val behavior = (fragment.requireDialog() as BottomSheetDialog).behavior
        assertTrue(
            "state ${behavior.state}",
            behavior.state == BottomSheetBehavior.STATE_EXPANDED || behavior.state == BottomSheetBehavior.STATE_SETTLING
        )
    }

    @Test
    fun shareEventSendsTheQrImageThroughAChooser() {
        val imageUri = Uri.parse("content://jp.co.soramitsu.test.provider/qr/address.png")
        mockkStatic(FileProvider::class)
        fileProviderMocked = true
        every { FileProvider.getUriForFile(any(), any(), any()) } returns imageUri
        val fragment = hostReceive()
        val activity = host!!.get()
        val qrFile = File("address.png")

        qrShareEvent.value = Event(QrSharingPayload(qrFile, "My Polkadot address"))
        ShadowLooper.idleMainLooper()

        verify { FileProvider.getUriForFile(any(), "${activity.packageName}.provider", qrFile) }
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(fragment.getString(R.string.wallet_receive_description), chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        val shared = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, shared.action)
        assertEquals("image/*", shared.type)
        assertEquals(imageUri, IntentCompat.getParcelableExtra(shared, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("My Polkadot address", shared.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleCarriesTheAsset() {
        val payload = AssetPayload("polkadot", "dot")

        assertEquals(payload, ReceiveFragment.getBundle(payload).get(ReceiveFragment.KEY_ASSET_PAYLOAD))
    }

    private fun hostReceive(): ReceiveFragment {
        val fragment = ReceiveFragment()
        host = HiltFragmentHost.host(fragment, mapOf(ReceiveViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return fragment
    }

    private fun receiveState() = ReceiveScreenViewState(
        qrCode = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
        assetSymbol = "DOT",
        account = WalletAccount("5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", "Main wallet"),
        multiToggleButtonState = MultiToggleButtonState(ReceiveToggleType.Receive, ReceiveToggleType.entries),
        amountInputViewState = AmountInputViewState(tokenName = "DOT", totalBalance = "Balance: 10 DOT", fiatAmount = "$6", tokenAmount = BigDecimal.ONE),
        requestAllowed = true,
        networkName = "Polkadot"
    )
}
