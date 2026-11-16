package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp")
class MnemonicAgreementsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : MnemonicAgreementsCallback {
        override fun onLosePhraseAgreementClick() { calls += "lose" }
        override fun onSharePhraseAgreementClick() { calls += "share" }
        override fun onKeepPhraseAgreementClick() { calls += "keep" }
        override fun onBackClick() { calls += "back" }
        override fun onShowPhrase() { calls += "show" }
    }

    private fun state(lose: Boolean, share: Boolean, keep: Boolean) = MnemonicAgreementsState(
        losePhraseAgreementItemState = TextSelectableItemState(lose, R.string.mnemonic_agreements_ageement_1),
        sharePhraseAgreementItemState = TextSelectableItemState(share, R.string.mnemonic_agreements_ageement_2),
        keepPhraseAgreementItemState = TextSelectableItemState(keep, R.string.mnemonic_agreements_ageement_3),
        isShowMnemonicButtonEnabled = lose && share && keep
    )

    @Test
    fun eachPromiseIsACheckboxAndThePhraseStaysHiddenUntilAllAreChecked() {
        compose.setContent { FearlessAppTheme { MnemonicAgreementsContent(state(true, false, false), callback) } }

        compose.onNodeWithText(string(R.string.mnemonic_agreements_title)).assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
        compose.onNodeWithText(string(R.string.backup_wallet_show_mnemonic_phrase)).assertIsNotEnabled().tap()

        compose.onNodeWithText(string(R.string.mnemonic_agreements_ageement_1)).tap()
        compose.onNodeWithText(string(R.string.mnemonic_agreements_ageement_2)).tap()
        compose.onNodeWithText(string(R.string.mnemonic_agreements_ageement_3)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("lose", "share", "keep", "back"), calls)
    }

    @Test
    fun allPromisesCheckedEnableShowingThePhrase() {
        compose.setContent { FearlessAppTheme { MnemonicAgreementsContent(state(true, true, true), callback) } }

        compose.onNodeWithText(string(R.string.backup_wallet_show_mnemonic_phrase)).assertIsEnabled().tap()
        compose.waitForIdle()

        assertEquals(listOf("show"), calls)
    }

    @Test
    fun previewShowsTheThreePromises() {
        compose.setContent {
            FearlessAppTheme {
                PrivateComposable(
                    "jp.co.soramitsu.account.impl.presentation.mnemonic_agreements.MnemonicAgreementsContentKt",
                    "PreviewMnemonicAgreementsContent"
                )
            }
        }

        compose.onNodeWithText(string(R.string.mnemonic_agreements_ageement_3)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.backup_wallet_show_mnemonic_phrase)).assertIsNotEnabled()
    }
}
