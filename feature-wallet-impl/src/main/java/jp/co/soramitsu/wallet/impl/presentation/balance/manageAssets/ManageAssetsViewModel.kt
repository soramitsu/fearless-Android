package jp.co.soramitsu.wallet.impl.presentation.balance.manageAssets

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.DragAndDropTouchHelperCallback
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ManageAssetsViewModel @Inject constructor(
    private val walletInteractor: WalletInteractor,
    private val walletRouter: WalletRouter,
    private val assetNotNeedAccountUseCase: AssetNotNeedAccountUseCase
) : BaseViewModel(), DragAndDropTouchHelperCallback.Listener {
    private val _showAddAccountChooser = MutableLiveData<Event<AddAccountBottomSheet.Payload>>()
    val showAddAccountChooser: LiveData<Event<AddAccountBottomSheet.Payload>> = _showAddAccountChooser

    private val searchQueryFlow = MutableStateFlow("")

    private var initialAssets: List<ManageAssetModel> = mutableListOf()

    private val _canApply = MutableLiveData(false)
    val canApply: LiveData<Boolean> = _canApply

    private val _unsyncedItemsFlow: MutableStateFlow<List<ManageAssetModel>> = MutableStateFlow(emptyList())

    private val _localAssetsFlow = walletInteractor.assetsFlow()
        .map { it.sortedBy { it.hasAccount || it.asset.markedNotNeed }.map { it.toAssetModel() } }

    private fun changeSortPositionWhenNotNeedChanged(assets: List<ManageAssetModel>, index: Int, model: ManageAssetModel): List<ManageAssetModel>? {
        assets.firstOrNull { it.chainId == model.chainId && it.tokenSymbol == model.tokenSymbol && it.accountId.contentEquals(model.accountId) }
            ?.let { assetInList ->
                if (assetInList.markedAsNotNeed) return@let

                val indexOf = assets.indexOf(assetInList)
                val newOrderedAssets = assets.toMutableList()
                newOrderedAssets.removeAt(indexOf)
                newOrderedAssets.add(index, model)
                return newOrderedAssets
            }
        return null
    }

    val unsyncedItemsFlow = combine(_unsyncedItemsFlow, searchQueryFlow, _localAssetsFlow) { assets, query, localAssets ->
        if (initialAssets.isNotEmpty()) {
            localAssets.mapIndexed { index, model ->
                if (model.markedAsNotNeed) {
                    changeSortPositionWhenNotNeedChanged(initialAssets, index, model)?.let { initialAssets = it }
                    changeSortPositionWhenNotNeedChanged(assets, index, model)?.let { _unsyncedItemsFlow.value = it }
                }
            }
        }
        if (query.isEmpty()) {
            assets
        } else {
            assets.filter {
                it.chainName.contains(query, ignoreCase = true) ||
                    it.tokenSymbol.contains(query, ignoreCase = true) ||
                    it.network?.name?.contains(query, ignoreCase = true) == true
            }
        }
    }

    init {
        launch {
            val first = walletInteractor.assetsFlow().first()
            initialAssets = first.filter { it.hasAccount || !it.hasChainAccount }.sortedBy { it.hasAccount || it.asset.markedNotNeed }.map { it.toAssetModel() }
            _unsyncedItemsFlow.value = initialAssets.toMutableList().map { it.copy() }
        }
    }

    fun toggleEnabled(item: ManageAssetModel) {
        item.enabled = item.enabled.not()
        onItemsChanged()
    }

    fun onAddAccountClick(chainId: ChainId, chainName: String, assetId: String, markedAsNotNeed: Boolean, priceId: String?) {
        launch {
            val meta = walletInteractor.getSelectedMetaAccount()
            _showAddAccountChooser.value = Event(
                AddAccountBottomSheet.Payload(
                    metaId = meta.id,
                    chainId = chainId,
                    chainName = chainName,
                    assetId = assetId,
                    markedAsNotNeed = markedAsNotNeed,
                    priceId = priceId
                )
            )
        }
    }

    fun createAccount(chainId: ChainId, metaId: Long) {
        walletRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = false)
    }

    fun importAccount(chainId: ChainId, metaId: Long) {
        walletRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = true)
    }

    fun noNeedAccount(chainId: ChainId, metaId: Long, assetId: String, priceId: String?) {
        launch {
            assetNotNeedAccountUseCase.markNotNeed(
                chainId = chainId,
                metaId = metaId,
                assetId = assetId,
                priceId = priceId
            )
        }
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
                val metaId = walletInteractor.getSelectedMetaAccount().id
                item.toUpdateItem(metaId, index)
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
                val areItemsTheSame = it.accountId.contentEquals(item.accountId) && it.chainId == item.chainId && it.id == item.assetId
                if (areItemsTheSame.not()) {
                    return true
                }
            }
        }
        return false
    }
}
