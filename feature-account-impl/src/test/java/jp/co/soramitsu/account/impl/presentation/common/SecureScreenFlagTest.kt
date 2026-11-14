package jp.co.soramitsu.account.impl.presentation.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureScreenFlagTest {

    private val window = FakeWindow()

    @Test
    fun `screen sets the flag and restores a non-secure window when it leaves`() {
        val export = SecureScreenFlag()

        export.show(window)
        assertTrue(window.isSecure)

        export.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `screen keeps a flag that was set before it appeared`() {
        window.addSecureFlag()
        val export = SecureScreenFlag()

        export.show(window)
        export.hide()

        assertTrue(window.isSecure)
    }

    @Test
    fun `show and hide are idempotent and hide before show does nothing`() {
        val export = SecureScreenFlag()

        export.hide()
        export.onLeaving()
        export.onIncomingShown()
        export.onOtherViewDestroyed()
        assertFalse(window.isSecure)

        export.show(window)
        export.show(window)
        assertTrue(window.isSecure)

        export.hide()
        export.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `export opened from the PIN screen stays secure after the PIN view is destroyed`() {
        val pin = RecordAndRestoreScreen(window)
        val export = SecureScreenFlag()
        pin.createView()

        // openAfterPinCode: the export view is created before the popped PIN
        // view is destroyed, and the PIN screen then restores "not secure".
        export.show(window)
        pin.destroyView()
        export.onOtherViewDestroyed()
        assertTrue(window.isSecure)

        // Back to the account screen.
        export.onLeaving()
        export.onIncomingShown()
        assertTrue("the exit animation stays covered", window.isSecure)
        export.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `export to confirm hands the flag over and back without gaps`() {
        val export = SecureScreenFlag()
        val confirm = SecureScreenFlag()
        export.show(window)

        // Push: export stops (removing) before confirm creates its view.
        export.onLeaving()
        confirm.show(window)
        export.onIncomingShown()
        assertTrue(window.isSecure)
        export.hide()
        confirm.onOtherViewDestroyed()
        assertTrue(window.isSecure)

        // Pop: confirm stops (removing) before export recreates its view.
        confirm.onLeaving()
        export.show(window)
        confirm.onIncomingShown()
        assertTrue(window.isSecure)
        confirm.hide()
        export.onOtherViewDestroyed()
        assertTrue(window.isSecure)

        // Leaving the flow restores the original state.
        export.onLeaving()
        export.onIncomingShown()
        export.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `PIN screen opened after confirmation stays secure and still clears the flag later`() {
        val confirm = SecureScreenFlag()
        val pin = RecordAndRestoreScreen(window)
        confirm.show(window)

        // openCreatePincode pushes the PIN screen over the confirmation.
        confirm.onLeaving()
        pin.createView()
        confirm.onIncomingShown()
        confirm.hide()
        assertTrue("the PIN screen must stay protected", window.isSecure)

        // The PIN screen recorded "not secure", so it restores that itself.
        pin.destroyView()
        assertFalse(window.isSecure)
    }

    @Test
    fun `returning from the PIN screen to the confirmation keeps it secure`() {
        val confirm = SecureScreenFlag()
        val pin = RecordAndRestoreScreen(window)
        confirm.show(window)
        confirm.onLeaving()
        pin.createView()
        confirm.onIncomingShown()
        confirm.hide()

        // Back from the PIN screen: confirmation's view returns first.
        confirm.show(window)
        pin.destroyView()
        confirm.onOtherViewDestroyed()
        assertTrue(window.isSecure)

        confirm.onLeaving()
        confirm.onIncomingShown()
        confirm.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `leaving for a normal screen keeps the outgoing view covered until destroyed`() {
        val export = SecureScreenFlag()
        export.show(window)

        export.onLeaving()
        assertFalse("the incoming screen sees the earlier state", window.isSecure)
        export.onIncomingShown()
        assertTrue(window.isSecure)

        export.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `stopping without being removed keeps the flag for the recents thumbnail`() {
        val export = SecureScreenFlag()
        export.show(window)

        // Home or another Activity on top: onStop is not a leave.
        export.show(window)
        assertTrue(window.isSecure)
    }

    @Test
    fun `cancelled back gesture keeps the screen secure`() {
        val export = SecureScreenFlag()
        val account = PlainScreen()
        export.show(window)

        export.onLeaving()
        account.createView()
        export.onIncomingShown()
        assertTrue(window.isSecure)

        // Cancel: export starts again and the preview view is destroyed.
        export.show(window)
        export.onOtherViewDestroyed()
        assertTrue(window.isSecure)

        export.hide()
        assertFalse(window.isSecure)
    }

    @Test
    fun `recreated Activity window is protected and the old one released`() {
        val oldWindow = FakeWindow()
        val newWindow = FakeWindow()
        val export = SecureScreenFlag()

        export.show(oldWindow)
        export.hide()
        export.show(newWindow)

        assertFalse(oldWindow.isSecure)
        assertTrue(newWindow.isSecure)
    }

    @Test
    fun `showing on another window releases the previous one`() {
        val oldWindow = FakeWindow()
        val newWindow = FakeWindow()
        val export = SecureScreenFlag()

        export.show(oldWindow)
        export.show(newWindow)

        assertFalse(oldWindow.isSecure)
        assertTrue(newWindow.isSecure)
    }

    /** The existing PIN / recovery-import pattern: record, add, restore. */
    private class RecordAndRestoreScreen(private val window: FakeWindow) {
        private var hadSecureFlag = false

        fun createView() {
            hadSecureFlag = window.isSecure
            window.addSecureFlag()
        }

        fun destroyView() {
            if (!hadSecureFlag) window.clearSecureFlag()
        }
    }

    /** A screen that never touches the flag. */
    private class PlainScreen {
        fun createView() = Unit
    }

    private class FakeWindow : SecureFlagWindow {
        override var isSecure: Boolean = false
            private set

        override fun addSecureFlag() {
            isSecure = true
        }

        override fun clearSecureFlag() {
            isSecure = false
        }
    }
}
