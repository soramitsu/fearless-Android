package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import androidx.lifecycle.SavedStateHandle
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MnemonicAgreementsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val types = listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(name: String? = "Savings") = MnemonicAgreementsViewModel(
        router,
        SavedStateHandle(
            buildMap<String, Any> {
                name?.let { put(MnemonicAgreementsDialog.WALLET_NAME_KEY, it) }
                put(MnemonicAgreementsDialog.ACCOUNT_TYPES_KEY, types)
            }
        )
    )

    @Test
    fun `the phrase button unlocks only after all three agreements and locks again when one is withdrawn`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val initial = vm.state.value
        assertFalse(initial.isShowMnemonicButtonEnabled)
        assertEquals(R.string.mnemonic_agreements_ageement_1, initial.losePhraseAgreementItemState.textResId)
        assertEquals(R.string.mnemonic_agreements_ageement_2, initial.sharePhraseAgreementItemState.textResId)
        assertEquals(R.string.mnemonic_agreements_ageement_3, initial.keepPhraseAgreementItemState.textResId)

        vm.onLosePhraseAgreementClick(); runCurrent()
        vm.onSharePhraseAgreementClick(); runCurrent()
        assertTrue(vm.state.value.losePhraseAgreementItemState.isSelected)
        assertTrue(vm.state.value.sharePhraseAgreementItemState.isSelected)
        assertFalse(vm.state.value.isShowMnemonicButtonEnabled)

        vm.onKeepPhraseAgreementClick(); runCurrent()
        assertTrue(vm.state.value.isShowMnemonicButtonEnabled)

        vm.onSharePhraseAgreementClick(); runCurrent()
        assertFalse(vm.state.value.sharePhraseAgreementItemState.isSelected)
        assertFalse(vm.state.value.isShowMnemonicButtonEnabled)
    }

    @Test
    fun `showing the phrase opens the backup mnemonic for the named wallet and its ecosystems`() {
        viewModel().onShowPhrase()
        viewModel(name = null).onShowPhrase()
        viewModel().onBackClick()

        verify { router.openMnemonicDialogGoogleBackup(accountName = "Savings", accountTypes = types) }
        verify { router.openMnemonicDialogGoogleBackup(accountName = "", accountTypes = types) }
        verify { router.back() }
    }

    @Test
    fun `the dialog cannot start without account types`() {
        assertThrows(IllegalStateException::class.java) {
            MnemonicAgreementsViewModel(router, SavedStateHandle(mapOf(MnemonicAgreementsDialog.WALLET_NAME_KEY to "x")))
        }
    }
}
