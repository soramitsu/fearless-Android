package jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonChainId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OptionsEcosystemAccountsViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountDetailsInteractor: AccountDetailsInteractor,
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter
) : BaseViewModel() {
    val walletId = savedStateHandle.get<Long>(OptionsEcosystemAccountsFragment.KEY_META_ID) ?: error("Wallet id not specified")
    val type = savedStateHandle.get<ImportAccountType>(OptionsEcosystemAccountsFragment.KEY_TYPE) ?: error("Account type not specified")

    val state: StateFlow<OptionsEcosystemAccountsScreenViewState> = flowOf {
        OptionsEcosystemAccountsScreenViewState(
            metaId = walletId,
            type = type,
            hasReplacedAccounts = accountDetailsInteractor.hasReplacedAccounts(walletId, type)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OptionsEcosystemAccountsScreenViewState(
            metaId = walletId,
            type = type,
            hasReplacedAccounts = true
        )
    )

    fun onBackupEcosystemAccountsClicked() {
        launch {
            accountInteractor.getBestBackupType(walletId, type)?.let {
                val usedChainId = when (type) {
                    ImportAccountType.Substrate -> polkadotChainId
                    ImportAccountType.Ethereum -> ethereumChainId
                    ImportAccountType.Ton -> tonChainId
                }
                when (it) {
                    BackupAccountType.PASSPHRASE -> {
                        val destination = accountRouter.getExportMnemonicDestination(walletId, usedChainId)
                        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
                    }
                    BackupAccountType.SEED -> {
                        val destination = accountRouter.getExportSeedDestination(walletId, usedChainId)
                        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
                    }
                    BackupAccountType.JSON -> { /* not used */ }
                }
            }
        }
    }

    fun onEcosystemAccountsClicked() {
        accountRouter.openEcosystemAccountsFragment(walletId, type)
    }

    fun onBackClicked() {
        accountRouter.back()
    }
}
