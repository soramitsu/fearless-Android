package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.DragAndDropTouchHelperCallback
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ManageAssetsViewModel(
    private val walletInteractor: WalletInteractor,
    private val walletRouter: WalletRouter
) :
    BaseViewModel(), DragAndDropTouchHelperCallback.Listener {

    private val searchQueryFlow = MutableStateFlow("")

    private lateinit var initialAssets: List<ManageAssetModel>

    private val _canApply = MutableLiveData(false)
    val canApply: LiveData<Boolean> = _canApply

    private val _unsyncedItemsFlow: MutableStateFlow<List<ManageAssetModel>> = MutableStateFlow(emptyList())
    val unsyncedItemsFlow = combine(_unsyncedItemsFlow, searchQueryFlow) { assets, query ->
        if (query.isEmpty()) {
            assets
        } else {
            assets.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.tokenSymbol.contains(query, ignoreCase = true) ||
                    it.network?.name?.contains(query, ignoreCase = true) == true
            }
        }
    }

    init {
        launch {
            initialAssets = walletInteractor.assetsFlow().first().map { it.toAssetModel() }
            _unsyncedItemsFlow.value = initialAssets.toMutableList().map { it.copy() }
        }
    }

    fun toggleEnabled(item: ManageAssetModel) {
        item.enabled = item.enabled.not()
        onItemsChanged()
    }

    fun addAccount() {
        walletRouter.openAddAccount()
    }

    fun searchQueryChanged(query: String) {
        searchQueryFlow.value = query.trimIndent()
    }

    fun backClicked() {
        walletRouter.back()
    }

    override fun onItemDrag(from: Int, to: Int) {
        launch {
            val list = _unsyncedItemsFlow.value.toMutableList()
            list.add(to, list.removeAt(from))
            _unsyncedItemsFlow.value = list
            onItemsChanged()
        }
    }

    private fun onItemsChanged() {
        launch {
            _canApply.postValue(_unsyncedItemsFlow.value != initialAssets)
        }
    }

    fun onApply() {
        launch {
            val newItems = _unsyncedItemsFlow.first().mapIndexed { index, item ->
                item.position = index
                item.toUpdateItem()
            }

            walletInteractor.updateAssets(newItems)

            if (isSortingUpdated(newItems)) {
                walletInteractor.enableCustomAssetSorting()
            }

            walletRouter.back()
        }
    }

    private fun isSortingUpdated(newItems: List<AssetUpdateItem>): Boolean {
        initialAssets.forEachIndexed { index, item ->
            newItems[index].let {
                val areItemsTheSame = it.accountId.contentEquals(item.accountId) && it.chainId == item.chainId && it.tokenSymbol == item.tokenSymbol
                if (areItemsTheSame.not()) {
                    return true
                }
            }
        }
        return false
    }
}
