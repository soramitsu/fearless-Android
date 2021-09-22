package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

class BalanceListViewModel(
    private val interactor: WalletInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val buyMixin: BuyMixin.Presentation,
    private val transactionHistoryMixin: TransactionHistoryMixin,
) : BaseViewModel(),
    TransactionHistoryUi by transactionHistoryMixin,
    BuyMixin by buyMixin {

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    val balanceLiveData = balanceFlow().asLiveData()

    private val primaryTokenLiveData = balanceLiveData.map { it.assetModels.first().token.configuration }

    val buyEnabledLiveData = primaryTokenLiveData.map(initial = false) {
        buyMixin.isBuyEnabled(it)
    }

    fun sync() {
        viewModelScope.launch {
            val deferredAssetSync = async(Dispatchers.Default) { interactor.syncAssetsRates() }
            val deferredTransactionsSync = async(Dispatchers.Default) { transactionHistoryMixin.syncFirstOperationsPage() }

            val results = awaitAll(deferredAssetSync, deferredTransactionsSync)

            val firstError = results.mapNotNull { it.exceptionOrNull() }
                .firstOrNull()

            firstError?.let(::showError)

            _hideRefreshEvent.value = Event(Unit)
        }
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

    fun assetClicked(asset: AssetModel) {
        router.openAssetDetails(asset.token.configuration)
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

        buyMixin.buyClicked(token, address)
    }

    fun avatarClicked() {
        router.openChangeAccountFromWallet()
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow(Node.NetworkType.POLKADOT.chainId) //  TODO stub
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    private fun balanceFlow(): Flow<BalanceModel> {
        return interactor.assetsFlow()
            .map {
                val assetModels = it.map(::mapAssetToAssetModel)

                BalanceModel(assetModels)
            }
    }
}
