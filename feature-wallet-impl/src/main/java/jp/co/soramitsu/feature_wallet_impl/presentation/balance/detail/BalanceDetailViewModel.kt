package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.TransactionHistoryElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private class TokenFilter(private val type: Token.Type) : TransactionFilter {
    override fun shouldInclude(model: TransactionHistoryElement): Boolean {
        return type == model.transactionModel.type
    }
}

class BalanceDetailViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val type: Token.Type,
    private val buyMixin: BuyMixin.Presentation,
    private val transactionHistoryMixin: TransactionHistoryMixin
) : BaseViewModel(),
    TransactionHistoryUi by transactionHistoryMixin,
    BuyMixin by buyMixin {

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showFrozenDetailsEvent = MutableLiveData<Event<AssetModel>>()
    val showFrozenDetailsEvent: LiveData<Event<AssetModel>> = _showFrozenDetailsEvent

    val assetLiveData = currentAssetFlow().asLiveData()

    val buyEnabled = buyMixin.buyEnabled(type)

    init {
        transactionHistoryMixin.startObservingTransactions(viewModelScope)

        transactionHistoryMixin.addFilter(viewModelScope, TokenFilter(type))
    }

    override fun onCleared() {
        super.onCleared()

        transactionHistoryMixin.clear()
    }

    fun transactionsScrolled(index: Int) {
        transactionHistoryMixin.scrolled(viewModelScope, index)
    }

    fun sync() {
        viewModelScope.launch {
            val deferredAssetSync = async { interactor.syncAssetRates(type) }
            val deferredTransactionsSync = async { transactionHistoryMixin.syncFirstTransactionsPage() }

            val results = awaitAll(deferredAssetSync, deferredTransactionsSync)

            val firstError = results.mapNotNull { it.exceptionOrNull() }
                .firstOrNull()

            firstError?.let(::showError)

            _hideRefreshEvent.value = Event(Unit)
        }
    }

    fun backClicked() {
        router.back()
    }

    fun sendClicked() {
        router.openChooseRecipient()
    }

    fun receiveClicked() {
        router.openReceive()
    }

    fun buyClicked() {
        viewModelScope.launch {
            val currentAccount = interactor.getSelectedAccount()

            buyMixin.startBuyProcess(type, currentAccount.address)
        }
    }

    fun frozenInfoClicked() {
        assetLiveData.value?.let {
            _showFrozenDetailsEvent.value = Event(it)
        }
    }

    private fun currentAssetFlow(): Flow<AssetModel> {
        return interactor.assetFlow(type)
            .map { mapAssetToAssetModel(it) }
    }
}