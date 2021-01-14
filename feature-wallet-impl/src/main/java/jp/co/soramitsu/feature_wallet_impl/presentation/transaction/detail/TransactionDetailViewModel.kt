package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

private const val ICON_SIZE_DP = 32

enum class ExternalActionsSource {
    TRANSACTION_HASH, FROM_ADDRESS, TO_ADDRESS
}

class TransactionDetailViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val appLinksProvider: AppLinksProvider,
    val transaction: TransactionModel
) : BaseViewModel(), Browserable {

    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalTransactionActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    val recipientAddressModelLiveData = getRecipientIcon()
        .asLiveData()

    val senderAddressModelLiveData = getSenderIcon()
        .asLiveData()

    val retryAddressModelLiveData = if (transaction.isIncome) senderAddressModelLiveData else recipientAddressModelLiveData

    fun copyStringClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    fun backClicked() {
        router.back()
    }

    fun repeatTransaction() {
        router.openRepeatTransaction(transaction.displayAddress)
    }

    private fun getRecipientIcon() = getIcon(transaction.recipientAddress)

    private fun getSenderIcon() = getIcon(transaction.senderAddress)

    private fun getIcon(address: String) = addressIconGenerator.createAddressModel(address, ICON_SIZE_DP)

    fun showExternalActionsClicked(externalActionsSource: ExternalActionsSource) {
        _showExternalViewEvent.value = Event(externalActionsSource)
    }

    fun viewTransactionExternalClicked(analyzer: ExternalAnalyzer, hash: String, networkType: Node.NetworkType) {
        val url = appLinksProvider.getExternalTransactionUrl(analyzer, hash, networkType)

        openBrowserEvent.value = Event(url)
    }

    fun viewAccountExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: Node.NetworkType) {
        val url = appLinksProvider.getExternalAddressUrl(analyzer, address, networkType)

        openBrowserEvent.value = Event(url)
    }
}
