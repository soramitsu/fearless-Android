package jp.co.soramitsu.account.impl.presentation.backup_wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BackupWalletViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val resourceManager: ResourceManager
) : BaseViewModel(), BackupWalletCallback {

    private val walletId = savedStateHandle.get<Long>(BackupWalletDialog.ACCOUNT_ID_KEY)!!
    private val wallet = flowOf {
        accountInteractor.getMetaAccount(walletId)
    }
    private val walletItem = wallet
        .map { wallet ->

            val icon = addressIconGenerator.createAddressIcon(
                wallet.substrateAccountId,
                AddressIconGenerator.SIZE_BIG
            )

            val balanceModel = totalBalanceUseCase(walletId)

            WalletItemViewState(
                id = walletId,
                title = wallet.name,
                isSelected = false,
                walletIcon = icon,
                balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
                )
            )
        }
    private val isDeleteWalletEnabled = wallet.map { !it.isSelected }

    val state = combine(
        walletItem,
        isDeleteWalletEnabled
    ) { walletItem, isDeleteWalletEnabled ->
        BackupWalletState(
            walletItem = walletItem,
            isWalletSavedInGoogle = true,
            isDeleteWalletEnabled = isDeleteWalletEnabled
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackupWalletState.Empty)

    override fun onBackClick() {
        accountRouter.back()
    }

    override fun onShowMnemonicPhraseClick() {
        // TODO: Only polkadotChainId?
        val destination = accountRouter.getExportMnemonicDestination(walletId, polkadotChainId)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onShowRawSeedClick() {
        // TODO: Only polkadotChainId?
        val destination = accountRouter.getExportSeedDestination(walletId, polkadotChainId)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onExportJsonClick() {
        // TODO: Only polkadotChainId?
        val destination = accountRouter.openExportJsonPasswordDestination(walletId, polkadotChainId)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onDeleteGoogleBackupClick() {
        // TODO
    }

    override fun onGoogleBackupClick() {
        // TODO
    }

    override fun onDeleteWalletClick() {
        showError(
            title = resourceManager.getString(R.string.account_delete_confirmation_title),
            message = resourceManager.getString(R.string.account_delete_confirmation_description),
            positiveButtonText = resourceManager.getString(R.string.account_delete_confirm),
            negativeButtonText = resourceManager.getString(R.string.common_cancel),
            positiveClick = {
                println("123")
            }
        )
        // TODO
    }
}
