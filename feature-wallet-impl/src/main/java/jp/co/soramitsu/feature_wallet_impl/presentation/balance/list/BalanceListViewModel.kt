package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel

// TODO use dp
private const val ICON_SIZE_IN_PX = 40

class BalanceListViewModel(
    private val interactor: WalletInteractor,
    private val iconGenerator: IconGenerator,
    private val router: WalletRouter,
    private val transactionHistoryMixin: TransactionHistoryMixin
) : BaseViewModel(), TransactionHistoryUi by transactionHistoryMixin {
    private var transactionsRefreshed: Boolean = false
    private var balanceRefreshed: Boolean = false

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val errorHandler: ErrorHandler = {
        showError(it.message!!)

        transactionsRefreshFinished()
        balanceRefreshFinished()
    }

    init {
        disposables += transactionHistoryMixin.transferHistoryDisposable

        transactionHistoryMixin.setTransactionErrorHandler(errorHandler)

        transactionHistoryMixin.setTransactionSyncedInterceptor { transactionsRefreshFinished() }
    }

    val userIconLiveData = getUserIcon().asLiveData { showError(it.message!!) }

    // TODO repeating code
    private fun getUserIcon(): Observable<PictureDrawable> {
        return interactor.observeSelectedAddressId()
            .subscribeOn(Schedulers.io())
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .observeOn(AndroidSchedulers.mainThread())
    }

    val balanceLiveData = getBalance().asLiveData()

    private fun getBalance(): Observable<BalanceModel> {
        return interactor.observeAssets()
            .subscribeOn(Schedulers.io())
            .mapList(::mapAssetToAssetModel)
            .map(::BalanceModel)
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun syncAssets() {
        disposables += interactor.syncAssets()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { balanceRefreshFinished() }
            .subscribeToError(errorHandler)
    }

    fun refresh() {
        transactionsRefreshed = false
        balanceRefreshed = false

        syncAssets()
        syncFirstTransactionsPage()
    }

    fun assetClicked(asset: AssetModel) {
        router.openAssetDetails(asset.token)
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

    fun sendClicked() {
        router.openChooseRecipient()
    }

    fun receiveClicked() {
        // TODO
    }
}