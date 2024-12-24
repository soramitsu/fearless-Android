package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.optionsaddaccount.OptionsAddAccountFragment.Companion.KEY_TYPE
import jp.co.soramitsu.account.impl.presentation.optionsaddaccount.OptionsAddAccountFragment.Companion.KEY_WALLET_ID
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class OptionsAddAccountViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter
) : BaseViewModel() {
    val walletId = savedStateHandle.get<Long>(KEY_WALLET_ID) ?: error("Wallet Id not provided")
    val type = savedStateHandle.get<ImportAccountType>(KEY_TYPE) ?: error("Account type not provided")

    fun createAccount() {
        launch {
            val accountName = accountInteractor.getMetaAccount(walletId).name
            accountRouter.openMnemonicScreenAddAccount(walletId, accountName, type)
        }
    }

    fun importAccount() {
            accountRouter.openSelectImportModeForResult()
                .onEach(::handleSelectedImportMode)
                .launchIn(viewModelScope)
    }

    fun onBackClicked() {
        accountRouter.back()
    }

    private fun handleSelectedImportMode(importMode: ImportMode) {
        accountRouter.openImportAddAccountScreen(
            walletId = walletId,
            importAccountType = type,
            importMode = importMode
        )
    }
}
