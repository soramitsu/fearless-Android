package jp.co.soramitsu.account.impl.presentation.view.advanced

import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdvancedBlockViewTest {
    private lateinit var view: AdvancedBlockView

    @Before
    fun setUp() {
        view = AdvancedBlockView(ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Soramitsu_Fearless))
    }

    private fun advanced() = view.findViewById<View>(R.id.advancedView)

    @Test
    fun `derivation path fields never learn, suggest, autofill or save the path`() {
        listOf(view.substrateDerivationPathEditText, view.ethereumDerivationPathEditText).forEach { field ->
            assertTrue(field.inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0)
            assertTrue(field.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
            assertTrue(field.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, field.importantForAutofill)
            assertFalse(field.isSaveEnabled)
        }
    }

    @Test
    fun `the advanced header toggles the block open and closed`() {
        view.collapse()
        assertEquals(View.GONE, advanced().visibility)
        view.findViewById<View>(R.id.advancedTv).performClick()
        assertEquals(View.VISIBLE, advanced().visibility)
        view.toggle()
        assertEquals(View.GONE, advanced().visibility)
        view.expand()
        assertEquals(View.VISIBLE, advanced().visibility)
    }

    @Test
    fun `paths and encryption round trip through the fields`() {
        view.setSubstrateDerivationPath("//hard/soft")
        view.setEthereumDerivationPath("m/44'/60'/0'/0/1")
        view.setSubstrateEncryption("Schnorrkel")

        assertEquals("//hard/soft", view.getSubstrateDerivationPath())
        assertEquals("m/44'/60'/0'/0/1", view.getEthereumDerivationPath())
        view.setSubstrateDerivationPath(null)
        assertEquals("", view.getSubstrateDerivationPath())
    }

    @Test
    fun `account types show only their own fields`() {
        view.configureForAccountType(WalletEcosystem.Substrate)
        assertEquals(View.VISIBLE, view.substrateDerivationPathField.visibility)
        assertEquals(View.VISIBLE, view.substrateDerivationPathHintView.visibility)
        assertEquals(View.GONE, view.ethereumDerivationPathField.visibility)

        view.configureForAccountType(WalletEcosystem.Ethereum)
        assertEquals(View.GONE, view.substrateDerivationPathField.visibility)
        assertEquals(View.VISIBLE, view.ethereumEncryptionTypeField.visibility)
        assertFalse(view.ethereumEncryptionTypeField.isEnabled)
        assertEquals(View.GONE, view.ethereumDerivationPathField.visibility)

        for (type in listOf(WalletEcosystem.Ton, WalletEcosystem.Bitcoin, WalletEcosystem.Solana, WalletEcosystem.Iroha)) {
            view.configureForAccountType(type)
            assertEquals(View.GONE, view.substrateEncryptionTypeField.visibility)
            assertEquals(View.GONE, view.ethereumEncryptionTypeField.visibility)
        }
    }

    @Test
    fun `disabled fields stay visible without their hints and enabling restores them`() {
        view.configure(AdvancedBlockView.FieldState.DISABLED)
        assertEquals(View.VISIBLE, view.substrateDerivationPathField.visibility)
        assertFalse(view.substrateDerivationPathField.isEnabled)
        assertEquals(View.GONE, view.substrateDerivationPathHintView.visibility)
        assertEquals(View.GONE, view.ethereumDerivationPathHintView.visibility)

        view.setEnabled(view.substrateDerivationPathField, true)
        assertTrue(view.substrateDerivationPathField.isEnabled)
        view.setEnabled(view.substrateDerivationPathField, false)
        assertFalse(view.substrateDerivationPathField.isEnabled)
    }

    @Test
    fun `the encryption selector reacts only while enabled`() {
        var clicks = 0
        view.setOnSubstrateEncryptionTypeClickListener { clicks++ }

        view.configure(view.substrateEncryptionTypeField, AdvancedBlockView.FieldState.NORMAL)
        view.substrateEncryptionTypeField.performClick()
        assertEquals(1, clicks)

        view.configure(view.substrateEncryptionTypeField, AdvancedBlockView.FieldState.DISABLED)
        view.substrateEncryptionTypeField.performClick()
        assertEquals(1, clicks)
    }
}
