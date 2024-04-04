package jp.co.soramitsu.wallet.impl.presentation.manageassets

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class ManageAssetsViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ManageAssetsContentInterface {

    private val initialAssetStates = MutableStateFlow<List<AssetBooleanState>>(emptyList())
    private val currentAssetStates = MutableStateFlow<List<AssetBooleanState>>(emptyList())

    private val selectedChainIdFlow = MutableStateFlow<ChainId?>(null)

    private val savedChainFlow = selectedChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val assetModelsFlow: Flow<List<AssetModel>> =
        combine(
            walletInteractor.assetsFlow(),
            selectedChainIdFlow
        ) { assets, chainId ->
            assets.filter {
                chainId == null || it.asset.token.configuration.chainId == chainId
            }
        }
            .mapList {
                when {
                    it.hasAccount -> it.asset
                    else -> null
                }
            }
            .map { it.filterNotNull() }
            .mapList { mapAssetToAssetModel(it) }


    private val enteredTokenQueryFlow = MutableStateFlow("")

    val state = MutableStateFlow(ManageAssetsScreenViewState.default)

    private fun subscribeScreenState() {
        savedChainFlow.onEach {
            state.value = state.value.copy(selectedChainName = it?.name ?: resourceManager.getString(R.string.chain_selection_all_networks))
        }.launchIn(this)

        combine(assetModelsFlow, enteredTokenQueryFlow, currentAssetStates) { assetModels, searchQuery, currentStates ->
            val sortedAssets = assetModels
                .filter {
                    it.token.configuration.isUtility
                }
                .filter {
                    searchQuery.isEmpty() ||
                            it.token.configuration.symbol.contains(searchQuery, true) ||
                            it.token.configuration.name.orEmpty().contains(searchQuery, true)
                }
                .map { model ->
                    model.copy(isHidden = currentStates.firstOrNull {
                        it.assetId == model.token.configuration.id && it.chainId == model.token.configuration.chainId
                    }?.value == false)
                }
                .sortedWith(compareBy<AssetModel> {
                    "skip" // it.isHidden == true
                }.thenByDescending {
                    it.fiatAmount.orZero()
                }.thenByDescending {
                    it.available.orZero()
                }.thenBy {
                    it.token.configuration.chainName
                })

            val assets = sortedAssets.map {
                it.toManageAssetItemState()
            }

            val groupedAssets: Map<String, List<ManageAssetItemState>> = assets.groupBy {
                it.symbol
            }

            groupedAssets to searchQuery
        }.onEach { (assets, searchQuery) ->
            state.value = state.value.copy(assets = assets, searchQuery = searchQuery)
        }.launchIn(this)
    }

    init {
        subscribeScreenState()

        accountInteractor.selectedMetaAccountFlow().map { it.id }.distinctUntilChanged().map {
            selectedChainIdFlow.value = walletInteractor.getSavedChainId(it)

            walletInteractor.assetsFlow().firstOrNull()?.let { assets ->
                val assetStates = assets.map {
                    AssetBooleanState(
                        chainId = it.asset.token.configuration.chainId,
                        assetId = it.asset.token.configuration.id,
                        value = it.asset.enabled != false
                    )
                }
                initialAssetStates.value = assetStates
                currentAssetStates.value = assetStates
            }
        }.launchIn(this)

        walletRouter.chainSelectorPayloadFlow.map { chainId ->
            val walletId = accountInteractor.selectedLightMetaAccount().id
            walletInteractor.saveChainId(walletId, chainId)
            selectedChainIdFlow.value = chainId
        }.launchIn(this)
    }

    private fun AssetModel.toManageAssetItemState() = ManageAssetItemState(
        id = token.configuration.id,
        imageUrl = token.configuration.iconUrl,
        chainName = token.configuration.chainName,
        assetName = token.configuration.name,
        symbol = token.configuration.symbol.uppercase(),
        amount = available.orZero().formatCrypto(),
        fiatAmount = getAsFiatWithCurrency(available) ?: "${token.fiatSymbol.orEmpty()}0",
        chainId = token.configuration.chainId,
        isChecked = isHidden != true,
        showEdit = false
    )

    override fun onSearchInput(input: String) {
        enteredTokenQueryFlow.value = input
    }

    override fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean) {
        currentAssetStates.value = currentAssetStates.value.map {
            if (it.assetId == assetItemState.id && it.chainId == assetItemState.chainId) {
                it.copy(value = checked)
            } else {
                it
            }
        }
    }

    override fun onItemClicked(assetItemState: ManageAssetItemState) {
    }

    override fun onEditClicked(assetItemState: ManageAssetItemState) {
    }

    override fun onDoneClicked() {
        walletRouter.back()
    }

    override fun onSelectedChainClicked() {
        launch {
            val selectedChainId = savedChainFlow.firstOrNull()?.id
            walletRouter.openSelectChain(selectedChainId, isFilteringEnabled = true)
        }
    }

    fun onDialogClose() {
        val initial = initialAssetStates.value
        val changes = currentAssetStates.value.filter {
            it !in initial
        }
        kotlinx.coroutines.MainScope().launch {
            walletInteractor.updateAssetsHiddenState(changes)
        }
    }
}

