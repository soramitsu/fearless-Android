package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.common.AddressIconGenerator
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

private const val ICON_SIZE_DP = 32

class TransactionDetailViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    val transaction: TransactionModel
) : BaseViewModel() {
    val recipientAddressModelLiveData = addressIconGenerator
        .createAddressIcon(transaction.recipientAddress, ICON_SIZE_DP)
        .asLiveData()

    val senderAddressModelLiveData = addressIconGenerator
        .createAddressIcon(transaction.senderAddress, ICON_SIZE_DP)
        .asLiveData()

    val retryAddressModelLiveData = if (transaction.isIncome) senderAddressModelLiveData else recipientAddressModelLiveData

    fun copyAddressClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    fun backClicked() {
        router.back()
    }

    fun repeatTransaction() {
        router.openRepeatTransaction(transaction.displayAddress)
    }
}
