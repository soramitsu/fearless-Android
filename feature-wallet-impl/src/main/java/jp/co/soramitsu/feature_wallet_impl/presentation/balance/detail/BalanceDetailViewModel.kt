package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.TransactionHistoryElement

private class TokenFilter(private val token: Asset.Token) : TransactionFilter {
    override fun shouldInclude(model: TransactionHistoryElement): Boolean {
        return token == model.transactionModel.token
    }
}

class BalanceDetailViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val token: Asset.Token,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val transactionHistoryMixin: TransactionHistoryMixin
) : BaseViewModel(), Browserable, TransactionHistoryUi by transactionHistoryMixin {

    private var transactionsRefreshed: Boolean = false
    private var balanceRefreshed: Boolean = false

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showFrozenDetailsEvent = MutableLiveData<Event<AssetModel>>()
    val showFrozenDetailsEvent: LiveData<Event<AssetModel>> = _showFrozenDetailsEvent

    private val errorHandler: ErrorHandler = {
        showError(it.message!!)

        transactionsRefreshFinished()
        balanceRefreshFinished()
    }

    val assetLiveData = observeAssetModel().asLiveData()

    val currentAccountLiveData = interactor.observeSelectedAccount().asLiveData()

    private val availableProvidersLiveData = assetLiveData.map {
        buyTokenRegistry.availableProviders(it.token)
    }

    val buyEnabledLiveData = availableProvidersLiveData.map { it.isNotEmpty() }

    init {
        disposables += transactionHistoryMixin.transferHistoryDisposable

        transactionHistoryMixin.setTransactionErrorHandler(errorHandler)

        transactionHistoryMixin.setTransactionSyncedInterceptor { transactionsRefreshFinished() }

        transactionHistoryMixin.addFilter(TokenFilter(token))
    }

    override fun onCleared() {
        super.onCleared()

        transactionHistoryMixin.clear()
    }

    fun syncAssetRates() {
        disposables += interactor.syncAssetRates(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { balanceRefreshFinished() }
            .subscribeToError(errorHandler)
    }

    fun refresh() {
        transactionsRefreshed = false
        balanceRefreshed = false

        syncAssetRates()
        syncFirstTransactionsPage()
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
        val token = assetLiveData.value?.token ?: return
        val address = currentAccountLiveData.value?.address ?: return
        val provider = availableProvidersLiveData.value?.firstOrNull() ?: return

        val url = provider.createPurchaseLink(token, address)

        openBrowserEvent.value = Event(url)
    }

    fun frozenInfoClicked() {
        assetLiveData.value?.let {
            _showFrozenDetailsEvent.value = Event(it)
        }
    }

    private fun observeAssetModel(): Observable<AssetModel> {
        return interactor.observeAsset(token)
            .subscribeOn(Schedulers.io())
            .map(::mapAssetToAssetModel)
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun transactionsRefreshFinished() {
        transactionsRefreshed = true

        maybeHideRefresh()
    }

    private fun balanceRefreshFinished() {
        balanceRefreshed = true

        maybeHideRefresh()
    }

    private fun maybeHideRefresh() {
        if (transactionsRefreshed && balanceRefreshed) {
            _hideRefreshEvent.value = Event(Unit)
        }
    }
}