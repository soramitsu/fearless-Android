package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MnemonicAgreementsViewModel @Inject constructor() : BaseViewModel(), MnemonicAgreementsCallback {

    private val isLosePhraseAgreementSelected = MutableStateFlow(false)
    private val isSharePhraseAgreementItemStateSelected = MutableStateFlow(false)
    private val isKeepPhraseAgreementItemStateSelected = MutableStateFlow(false)
    private val isShowMnemonicButtonEnabled = combine(
        isLosePhraseAgreementSelected,
        isSharePhraseAgreementItemStateSelected,
        isKeepPhraseAgreementItemStateSelected
    ) {
            isLosePhraseAgreementSelected,
            isSharePhraseAgreementItemStateSelected,
            isKeepPhraseAgreementItemStateSelected ->
        isLosePhraseAgreementSelected &&
            isSharePhraseAgreementItemStateSelected &&
            isKeepPhraseAgreementItemStateSelected
    }

    private val initialState = MnemonicAgreementsState(
        losePhraseAgreementItemState = TextSelectableItemState(
            isSelected = isLosePhraseAgreementSelected.value,
            textResId = R.string.mnemonic_agreements_ageement_1
        ),
        sharePhraseAgreementItemState = TextSelectableItemState(
            isSelected = isSharePhraseAgreementItemStateSelected.value,
            textResId = R.string.mnemonic_agreements_ageement_2
        ),
        keepPhraseAgreementItemState = TextSelectableItemState(
            isSelected = isKeepPhraseAgreementItemStateSelected.value,
            textResId = R.string.mnemonic_agreements_ageement_3
        ),
        isShowMnemonicButtonEnabled = false
    )
    val state = combine(
        isLosePhraseAgreementSelected,
        isSharePhraseAgreementItemStateSelected,
        isKeepPhraseAgreementItemStateSelected,
        isShowMnemonicButtonEnabled
    ) {
            isLosePhraseAgreementSelected,
            isSharePhraseAgreementItemStateSelected,
            isKeepPhraseAgreementItemStateSelected,
            isShowMnemonicButtonEnabled ->
        MnemonicAgreementsState(
            losePhraseAgreementItemState = TextSelectableItemState(
                isSelected = isLosePhraseAgreementSelected,
                textResId = R.string.mnemonic_agreements_ageement_1
            ),
            sharePhraseAgreementItemState = TextSelectableItemState(
                isSelected = isSharePhraseAgreementItemStateSelected,
                textResId = R.string.mnemonic_agreements_ageement_2
            ),
            keepPhraseAgreementItemState = TextSelectableItemState(
                isSelected = isKeepPhraseAgreementItemStateSelected,
                textResId = R.string.mnemonic_agreements_ageement_3
            ),
            isShowMnemonicButtonEnabled = isShowMnemonicButtonEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialState)

    override fun onLosePhraseAgreementClick() {
        isLosePhraseAgreementSelected.value = !isLosePhraseAgreementSelected.value
    }

    override fun onSharePhraseAgreementClick() {
        isSharePhraseAgreementItemStateSelected.value = !isSharePhraseAgreementItemStateSelected.value
    }

    override fun onKeepPhraseAgreementClick() {
        isKeepPhraseAgreementItemStateSelected.value = !isKeepPhraseAgreementItemStateSelected.value
    }

    override fun onShowPhrase() {
    }

    override fun onBackClick() {
    }
}
