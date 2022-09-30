package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.account.api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class BalanceDetailViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val buyMixin: BuyMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    savedStateHandle: SavedStateHandle,
    addressIconGenerator: AddressIconGenerator,
    resourceManager: ResourceManager,
    addressDisplayUseCase: AddressDisplayUseCase
) : BaseViewModel(),
    TransactionHistoryUi,
    ExternalAccountActions by externalAccountActions,
    BuyMixin by buyMixin {

    private val assetPayload: AssetPayload = savedStateHandle[KEY_ASSET_PAYLOAD]!!

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

    private val transactionHistoryMixin = TransactionHistoryProvider(
        interactor,
        addressIconGenerator,
        router,
        HistoryFiltersProvider(),
        resourceManager,
        addressDisplayUseCase,
        savedStateHandle[KEY_ASSET_PAYLOAD]!!
    )

    override val state: Flow<TransactionHistoryUi.State> = transactionHistoryMixin.state

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

    override fun transactionClicked(transactionModel: OperationModel) {
        transactionHistoryMixin.transactionClicked(transactionModel)
    }
}
