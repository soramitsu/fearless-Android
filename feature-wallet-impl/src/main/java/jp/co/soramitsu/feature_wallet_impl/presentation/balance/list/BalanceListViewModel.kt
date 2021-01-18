package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40
private const val CHOOSER_ICON_SIZE = 24

class BalanceListViewModel(
    private val interactor: WalletInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val buyMixin: BuyMixin.Presentation,
    private val transactionHistoryMixin: TransactionHistoryMixin
) : BaseViewModel(),
    TransactionHistoryUi by transactionHistoryMixin,
    BuyMixin by buyMixin {

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showAccountChooser = MutableLiveData<Event<Payload<AddressModel>>>()
    val showAccountChooser: LiveData<Event<Payload<AddressModel>>> = _showAccountChooser

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    val balanceLiveData = balanceFlow().asLiveData()

    private val primaryTokenLiveData = balanceLiveData.map { it.assetModels.first().token.type }

    val buyEnabledLiveData = primaryTokenLiveData.map(initial = false) {
        buyMixin.buyEnabled(it)
    }

    init {
        transactionHistoryMixin.startObservingTransactions(viewModelScope)
    }

    fun sync() {
        viewModelScope.launch {
            val deferredAssetSync = async { interactor.syncAssetsRates() }
            val deferredTransactionsSync = async { transactionHistoryMixin.syncFirstTransactionsPage() }

            val results = awaitAll(deferredAssetSync, deferredTransactionsSync)

            val firstError = results.mapNotNull { it.exceptionOrNull() }
                .firstOrNull()

            firstError?.let(::showError)

            _hideRefreshEvent.value = Event(Unit)
        }
    }

    fun transactionsScrolled(index: Int) {
        transactionHistoryMixin.scrolled(viewModelScope, index)
    }

    fun assetClicked(asset: AssetModel) {
        router.openAssetDetails(asset.token.type)
    }

    fun sendClicked() {
        router.openChooseRecipient()
    }

    fun receiveClicked() {
        router.openReceive()
    }

    fun buyClicked() {
        val address = currentAddressModelLiveData.value?.address ?: return
        val token = primaryTokenLiveData.value ?: return

        buyMixin.startBuyProcess(token, address)
    }

    fun accountSelected(addressModel: AddressModel) {
        viewModelScope.launch {
            interactor.selectAccount(addressModel.address)

            val result = transactionHistoryMixin.syncFirstTransactionsPage()

            result.exceptionOrNull()?.let(::showError)
        }
    }

    fun avatarClicked() {
        val currentAddressModel = currentAddressModelLiveData.value!!

        viewModelScope.launch {
            val accounts = interactor.getAccountsInCurrentNetwork()

            val addressModels = accounts.map { generateAddressModel(it, CHOOSER_ICON_SIZE) }

            val chooserPayload = Payload(addressModels, currentAddressModel)

            _showAccountChooser.value = Event(chooserPayload)
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: Account, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp)
    }

    private fun balanceFlow(): Flow<BalanceModel> {
        return interactor.assetsFlow()
            .map {
                val assetModels = it.map(::mapAssetToAssetModel)

                BalanceModel(assetModels)
            }
    }
}