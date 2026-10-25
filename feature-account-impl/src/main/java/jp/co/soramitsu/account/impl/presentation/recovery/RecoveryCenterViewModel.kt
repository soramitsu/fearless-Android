package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RecoveryCenterViewModel @Inject constructor(
    inventory: WalletRecoveryInventory,
    private val router: AccountRouter
) : BaseViewModel() {
    val state = inventory.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), WalletRecoveryInventory.State(emptyList(), null, loading = true)
    )

    fun retryPreviousStorage() = router.openPreviousSecureStorageRecovery()
    fun resetPin() = router.openChangePinCode()
    fun back() = router.back()
    fun restore(target: RecoveryTarget, mode: ImportMode) = router.openRebind(target, mode)
    fun backupWallet(metaId: Long, chainId: String?) {
        if (chainId == null) router.openBackupWalletScreen(metaId)
        else router.withPinCodeCheckRequired(
            router.openExportJsonPasswordDestination(metaId, chainId),
            pinCodeTitleRes = jp.co.soramitsu.feature_account_impl.R.string.account_export
        )
    }
    fun exportStoredData() = router.openWalletEmergencyExport()
    fun savedEndpoints() = router.openSavedEndpoints()
    fun archivedData() = router.openArchivedWalletRecovery()
    fun importCorrected(target: RecoveryTarget) = router.openImportAccountScreen(target.ecosystem(), ImportMode.MnemonicPhrase)
}
