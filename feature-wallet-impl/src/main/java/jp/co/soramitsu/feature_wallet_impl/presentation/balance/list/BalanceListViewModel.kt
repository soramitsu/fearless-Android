package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.common.utils.zipSimilar
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi

private const val CURRENT_ICON_SIZE = 40
private const val CHOOSER_ICON_SIZE = 24

class BalanceListViewModel(
    private val interactor: WalletInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val transactionHistoryMixin: TransactionHistoryMixin
) : BaseViewModel(), TransactionHistoryUi by transactionHistoryMixin {

    private var transactionsRefreshed: Boolean = false
    private var balanceRefreshed: Boolean = false

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showAccountChooser = MutableLiveData<Event<Payload<AddressModel>>>()
    val showAccountChooser: LiveData<Event<Payload<AddressModel>>> = _showAccountChooser

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

    val currentAddressModelLiveData = getCurrentAddressModel().asLiveData { showError(it.message!!) }

    val balanceLiveData = getBalance().asLiveData()

    fun syncAssetsRates() {
        disposables += interactor.syncAssetsRates()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { balanceRefreshFinished() }
            .subscribeToError(errorHandler)
    }

    fun refresh() {
        transactionsRefreshed = false
        balanceRefreshed = false

        syncAssetsRates()
        syncFirstTransactionsPage()
    }

    fun assetClicked(asset: AssetModel) {
        router.openAssetDetails(asset.token)
    }

    fun sendClicked() {
        router.openChooseRecipient()
    }

    fun receiveClicked() {
        router.openReceive()
    }

    fun accountSelected(addressModel: AddressModel) {
        disposables += interactor.selectAccount(addressModel.address)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                syncFirstTransactionsPage()
            }, {
                showError(it.message!!)
            })
    }

    fun avatarClicked() {
        val currentAddressModel = currentAddressModelLiveData.value ?: return

        disposables += interactor.getAccountsInCurrentNetwork()
            .subscribeOn(Schedulers.io())
            .flatMap { accounts ->
                accounts.map { account -> generateAddressModel(account, CHOOSER_ICON_SIZE) }
                    .zipSimilar()
            }
            .map { models ->
                val selected = models.first { it.address == currentAddressModel.address }

                Payload(models, selected)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _showAccountChooser.value = Event(it)
            }, {
                showError(it.message!!)
            })
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

    private fun getCurrentAddressModel(): Observable<AddressModel> {
        return interactor.observeSelectedAccount()
            .subscribeOn(Schedulers.io())
            .flatMapSingle { generateAddressModel(it, CURRENT_ICON_SIZE) }
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun generateAddressModel(account: Account, sizeInDp: Int): Single<AddressModel> {
        return interactor.getAddressId(account.address).flatMap { id ->
            addressIconGenerator.createAddressModel(account.address, id, sizeInDp)
        }
    }

    private fun getBalance(): Observable<BalanceModel> {
        return interactor.observeAssets()
            .subscribeOn(Schedulers.io())
            .mapList(::mapAssetToAssetModel)
            .map(::BalanceModel)
            .observeOn(AndroidSchedulers.mainThread())
    }
}