package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
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

    val recipientAddressModelLiveData = getRecipientIcon()
        .asLiveData()

    val senderAddressModelLiveData = getSenderIcon()
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

    private fun getRecipientIcon() = interactor.getAddressId(transaction.recipientAddress)
        .flatMap { addressIconGenerator.createAddressIcon(transaction.recipientAddress, it, ICON_SIZE_DP) }

    private fun getSenderIcon() = interactor.getAddressId(transaction.senderAddress)
        .flatMap { addressIconGenerator.createAddressIcon(transaction.senderAddress, it, ICON_SIZE_DP) }
}
