package jp.co.soramitsu.app.root.presentation.emergency

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.idleMainUntil
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryEntry
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryField
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryFieldType
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryKeyKind
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryKeyReference
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryPage
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryPageChangedException
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryState
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthentication
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import jp.co.soramitsu.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class WalletEmergencyExportActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authenticator = mockk<WalletEmergencyAuthenticator>()
    private val reader = mockk<WalletEmergencyRecoveryReader>()
    private lateinit var controller: ActivityController<WalletEmergencyExportActivity>
    private val activity get() = controller.get()

    private val firstKey = EmergencyRecoveryKeyReference("wallet-7-substrate", 7, EmergencyRecoveryState.ACTIVE, EmergencyRecoveryKeyKind.SUBSTRATE)
    private val secondKey = EmergencyRecoveryKeyReference("wallet-7-ton", 7, EmergencyRecoveryState.SET_ASIDE, EmergencyRecoveryKeyKind.TON)
    private val orphanKey = EmergencyRecoveryKeyReference("legacy-v1", null, EmergencyRecoveryState.ARCHIVED, EmergencyRecoveryKeyKind.LEGACY_V1)

    @Before
    fun setUp() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        HiltShim.reset()
        HiltShim.inject(WalletEmergencyExportActivity::class) { screen ->
            screen.authenticator = authenticator
            screen.reader = reader
        }
        controller = Robolectric.buildActivity(WalletEmergencyExportActivity::class.java, WalletEmergencyExportActivity.intent(context)).setup()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        HiltShim.reset()
    }

    @Test
    fun opensSecuredOnThePinStepAndCloseFinishes() {
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertEquals(View.VISIBLE, view(R.id.emergencyPinGroup).visibility)
        assertEquals(string(CommonR.string.wallet_emergency_intro), text(R.id.emergencyMessage))
        assertEquals(View.GONE, view(R.id.emergencyPinError).visibility)
        view(R.id.emergencyClose).performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun wrongPinIsReportedAndTheTypedCodeIsCleared() {
        every { authenticator.authenticateWithKey("1111") } returns (WalletEmergencyAuthentication.WrongPin to null)
        unlock("1111")
        idleMainUntil { view(R.id.emergencyPinError).visibility == View.VISIBLE }
        assertEquals(string(CommonR.string.wallet_emergency_wrong_pin), text(R.id.emergencyPinError))
        assertEquals("", (view(R.id.emergencyPin) as EditText).text.toString())
        assertEquals(View.VISIBLE, view(R.id.emergencyPinGroup).visibility)
    }

    @Test
    fun lockedPinShowsTheWaitInWholeSeconds() {
        every { authenticator.authenticateWithKey("2222") } returns (WalletEmergencyAuthentication.Locked(1_500) to null)
        unlock("2222")
        idleMainUntil { view(R.id.emergencyPinError).visibility == View.VISIBLE }
        assertEquals(activity.getString(CommonR.string.wallet_emergency_locked, 2), text(R.id.emergencyPinError))
    }

    @Test
    fun unavailablePinHidesEntryWhenKeysExistAndReportsEmptyStorageOtherwise() {
        every { authenticator.authenticateWithKey(any()) } returns (WalletEmergencyAuthentication.Unavailable to null)
        coEvery { reader.hasStoredKeys() } returns true
        unlock("3333")
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_unavailable) }
        assertEquals(View.GONE, view(R.id.emergencyPinGroup).visibility)

        coEvery { reader.hasStoredKeys() } returns false
        controller.pause().stop().start().resume()
        unlock("3333")
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_empty) }
        assertEquals(View.GONE, view(R.id.emergencyPinGroup).visibility)
        assertEquals(0, entries().childCount)
    }

    @Test
    fun authenticatedListGroupsKeysByWalletAndPagesForward() {
        every { authenticator.authenticateWithKey("4444") } returns (WalletEmergencyAuthentication.Authenticated to null)
        coEvery { reader.listPage(null) } returns EmergencyRecoveryPage(listOf(firstKey, secondKey, orphanKey), "cursor-1")
        coEvery { reader.listPage("cursor-1") } returns EmergencyRecoveryPage(emptyList(), null)
        unlock("4444")
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_select_key) }

        val texts = entryTexts()
        assertEquals(1, texts.count { it == activity.getString(CommonR.string.wallet_emergency_wallet, 7L) })
        assertTrue(texts.contains(string(CommonR.string.wallet_emergency_wallet_unknown)))
        assertTrue(texts.contains(string(CommonR.string.wallet_emergency_kind_substrate) + " · " + string(CommonR.string.wallet_emergency_state_active)))
        assertTrue(texts.contains(string(CommonR.string.wallet_emergency_kind_ton) + " · " + string(CommonR.string.wallet_emergency_state_set_aside)))
        assertTrue(texts.contains(string(CommonR.string.wallet_emergency_kind_early) + " · " + string(CommonR.string.wallet_emergency_state_archived)))
        val viewButtons = buttons(string(CommonR.string.wallet_emergency_view_key))
        assertEquals(3, viewButtons.size)
        assertEquals(
            string(CommonR.string.wallet_emergency_view_key) + " · " + activity.getString(CommonR.string.wallet_emergency_key_identifier, "wallet-7-substrate"),
            viewButtons.first().contentDescription
        )
        assertEquals(View.GONE, view(R.id.emergencyPinGroup).visibility)

        buttons(string(CommonR.string.wallet_emergency_next_page)).single().performClick()
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_no_more_keys) }
        assertTrue(buttons(string(CommonR.string.wallet_emergency_next_page)).isEmpty())
        assertEquals(1, buttons(string(CommonR.string.wallet_emergency_first_page)).size)
        coVerify { reader.listPage("cursor-1") }
    }

    @Test
    fun revealedKeyShowsEveryFieldAndCopiesOnlyThroughTheSensitiveClipboard() {
        every { authenticator.authenticateWithKey("5555") } returns (WalletEmergencyAuthentication.Authenticated to null)
        coEvery { reader.listPage(null) } returns EmergencyRecoveryPage(listOf(firstKey), null)
        coEvery { reader.readKey("wallet-7-substrate", any()) } returns EmergencyRecoveryEntry(
            storageKey = "wallet-7-substrate",
            walletId = 7,
            state = EmergencyRecoveryState.ACTIVE,
            kind = EmergencyRecoveryKeyKind.SUBSTRATE,
            readable = false,
            fields = listOf(
                EmergencyRecoveryField(EmergencyRecoveryFieldType.RECOVERY_PHRASE, "alpha beta gamma"),
                EmergencyRecoveryField(EmergencyRecoveryFieldType.DERIVATION_PATH, "//hard")
            )
        )
        unlock("5555")
        idleMainUntil { buttons(string(CommonR.string.wallet_emergency_view_key)).isNotEmpty() }
        buttons(string(CommonR.string.wallet_emergency_view_key)).single().performClick()
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_title) }

        assertEquals(View.VISIBLE, view(R.id.emergencyWarning).visibility)
        val texts = entryTexts()
        assertTrue(texts.contains("alpha beta gamma"))
        assertTrue(texts.contains("//hard"))
        assertTrue(texts.contains(string(CommonR.string.wallet_emergency_unreadable)))
        assertTrue(texts.contains(string(CommonR.string.wallet_emergency_field_phrase)))
        val phrase = entryViews().filterIsInstance<TextView>().first { it.text.toString() == "alpha beta gamma" }
        assertFalse(phrase.isTextSelectable)

        val copyButtons = buttons(string(CommonR.string.common_copy))
        assertEquals(2, copyButtons.size)
        copyButtons.first().performClick()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals("alpha beta gamma", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        assertEquals(string(CommonR.string.common_copied), ShadowToast.getTextOfLatestToast())

        buttons(string(CommonR.string.wallet_emergency_back_to_list)).single().performClick()
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_select_key) }
        assertEquals(View.GONE, view(R.id.emergencyWarning).visibility)
    }

    @Test
    fun changedInventoryOffersTheFirstPageAgain() {
        every { authenticator.authenticateWithKey("6666") } returns (WalletEmergencyAuthentication.Authenticated to null)
        coEvery { reader.listPage(null) } returns EmergencyRecoveryPage(listOf(firstKey), "next")
        coEvery { reader.listPage("next") } throws EmergencyRecoveryPageChangedException()
        unlock("6666")
        idleMainUntil { buttons(string(CommonR.string.wallet_emergency_next_page)).isNotEmpty() }
        buttons(string(CommonR.string.wallet_emergency_next_page)).single().performClick()
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_inventory_changed) }
        buttons(string(CommonR.string.wallet_emergency_first_page)).single().performClick()
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_select_key) }
    }

    @Test
    fun readFailuresReturnToThePinStepWithAnError() {
        every { authenticator.authenticateWithKey("7777") } throws IllegalStateException("storage")
        unlock("7777")
        idleMainUntil { view(R.id.emergencyPinError).visibility == View.VISIBLE }
        assertEquals(string(CommonR.string.wallet_emergency_read_failed), text(R.id.emergencyPinError))

        every { authenticator.authenticateWithKey("8888") } returns (WalletEmergencyAuthentication.Authenticated to null)
        coEvery { reader.listPage(null) } returns EmergencyRecoveryPage(listOf(firstKey), null)
        coEvery { reader.readKey(any(), any()) } throws IllegalStateException("decrypt")
        unlock("8888")
        idleMainUntil { buttons(string(CommonR.string.wallet_emergency_view_key)).isNotEmpty() }
        buttons(string(CommonR.string.wallet_emergency_view_key)).single().performClick()
        idleMainUntil { view(R.id.emergencyPinGroup).visibility == View.VISIBLE }
        assertEquals(string(CommonR.string.wallet_emergency_read_failed), text(R.id.emergencyPinError))
    }

    @Test
    fun stoppingTheScreenRevokesRevealedKeys() {
        every { authenticator.authenticateWithKey("9999") } returns (WalletEmergencyAuthentication.Authenticated to null)
        coEvery { reader.listPage(null) } returns EmergencyRecoveryPage(listOf(firstKey), null)
        unlock("9999")
        idleMainUntil { text(R.id.emergencyMessage) == string(CommonR.string.wallet_emergency_select_key) }
        controller.pause().stop()
        assertEquals(View.VISIBLE, view(R.id.emergencyPinGroup).visibility)
        assertEquals(0, entries().childCount)
        assertNotEquals(string(CommonR.string.wallet_emergency_select_key), text(R.id.emergencyMessage))
        controller.start().resume()
    }

    private fun unlock(code: String) {
        (view(R.id.emergencyPin) as EditText).setText(code)
        view(R.id.emergencyUnlock).performClick()
    }

    private fun view(id: Int): View = activity.findViewById(id)
    private fun text(id: Int): String = (view(id) as TextView).text.toString()
    private fun string(id: Int): String = activity.getString(id)
    private fun entries(): ViewGroup = view(R.id.emergencyEntries) as ViewGroup
    private fun entryViews(): List<View> = (0 until entries().childCount).map { entries().getChildAt(it) }
    private fun entryTexts(): List<String> = entryViews().filterIsInstance<TextView>().map { it.text.toString() }
    private fun buttons(label: String): List<Button> =
        entryViews().filterIsInstance<Button>().filter { it.text.toString() == label }
}
