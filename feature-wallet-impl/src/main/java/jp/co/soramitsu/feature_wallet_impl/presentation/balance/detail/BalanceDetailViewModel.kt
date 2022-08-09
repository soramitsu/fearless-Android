package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.feature_account_api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BalanceDetailViewModel @AssistedInject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    @Assisted private val assetPayload: AssetPayload,
    private val buyMixin: BuyMixin.Presentation,
    private val transactionHistoryMixin: TransactionHistoryMixin,
    private val externalAccountActions: ExternalAccountActions.Presentation
) : BaseViewModel(),
    TransactionHistoryUi by transactionHistoryMixin,
    ExternalAccountActions by externalAccountActions,
    BuyMixin by buyMixin {

    private val _showAccountOptions = MutableLiveData<Event<String>>()
    val showAccountOptions: LiveData<Event<String>> = _showAccountOptions

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showFrozenDetailsEvent = MutableLiveData<Event<AssetModel>>()
    val showFrozenDetailsEvent: LiveData<Event<AssetModel>> = _showFrozenDetailsEvent

    val assetLiveData = currentAssetFlow().asLiveData()

    val buyEnabled = buyMixin.isBuyEnabled(assetPayload.chainId, assetPayload.chainAssetId)

    init {
        transactionHistoryMixin.setAssetPayload(assetPayload)
    }

    override fun onCleared() {
        super.onCleared()

        transactionHistoryMixin.cancel()
    }

    fun transactionsScrolled(index: Int) {
        transactionHistoryMixin.scrolled(index)
    }

    fun filterClicked() {
        router.openFilter()
    }

    fun sync() {
        viewModelScope.launch {
            async { transactionHistoryMixin.syncFirstOperationsPage() }.start()

            val deferredAssetSync = async { interactor.syncAssetsRates() }
            deferredAssetSync.await().exceptionOrNull()?.message?.let(::showMessage)

            _hideRefreshEvent.value = Event(Unit)
        }
    }

    fun backClicked() {
        router.back()
    }

    fun sendClicked() {
        router.openChooseRecipient(assetPayload)
    }

    fun receiveClicked() {
        router.openReceive(assetPayload)
    }

    fun accountOptionsClicked() = launch {
        interactor.getChainAddressForSelectedMetaAccount(assetPayload.chainId)?.let { address ->
            _showAccountOptions.postValue(Event(address))
        }
    }

    fun buyClicked() {
        viewModelScope.launch {
            interactor.selectedAccountFlow(assetPayload.chainId).firstOrNull()?.let { wallet ->
                buyMixin.buyClicked(assetPayload.chainId, assetPayload.chainAssetId, wallet.address)
            }
        }
    }

    fun frozenInfoClicked() {
        assetLiveData.value?.let {
            _showFrozenDetailsEvent.value = Event(it)
        }
    }

    private fun currentAssetFlow(): Flow<AssetModel> {
        return interactor.assetFlow(assetPayload.chainId, assetPayload.chainAssetId)
            .map { mapAssetToAssetModel(it) }
    }

    fun switchNode() {
        router.openNodes(assetPayload.chainId)
    }

    fun exportClicked() {
        viewModelScope.launch {
            val isEthereumBased = interactor.getChain(assetPayload.chainId).isEthereumBased
            val sources = interactor.getMetaAccountSecrets().buildExportSourceTypes(isEthereumBased)
            _showExportSourceChooser.value = Event(ExportSourceChooserPayload(assetPayload.chainId, sources))
        }
    }

    fun exportTypeSelected(selected: ExportSource, chainId: ChainId) {
        launch {
            val metaId = interactor.getSelectedMetaAccount().id
            val destination = when (selected) {
                is ExportSource.Json -> router.openExportJsonPassword(metaId, chainId)
                is ExportSource.Seed -> router.openExportSeed(metaId, chainId)
                is ExportSource.Mnemonic -> router.openExportMnemonic(metaId, chainId)
            }

            router.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
        }
    }

    @AssistedFactory
    interface BalanceDetailViewModelFactory {
        fun create(assetPayload: AssetPayload): BalanceDetailViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: BalanceDetailViewModelFactory,
            assetPayload: AssetPayload
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(assetPayload) as T
            }
        }
    }
}
