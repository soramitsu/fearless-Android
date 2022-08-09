package jp.co.soramitsu.featurewalletimpl.presentation.balance.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.FiatCurrencies
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.get
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletInteractor
import jp.co.soramitsu.featurewalletapi.domain.model.WalletAccount
import jp.co.soramitsu.featurewalletimpl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.featurewalletimpl.presentation.AssetPayload
import jp.co.soramitsu.featurewalletimpl.presentation.WalletRouter
import jp.co.soramitsu.featurewalletimpl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.featurewalletimpl.presentation.model.AssetModel
import jp.co.soramitsu.featurewalletimpl.presentation.model.AssetUpdateState
import jp.co.soramitsu.featurewalletimpl.presentation.model.AssetWithStateModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

@HiltViewModel
class BalanceListViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val updatesMixin: UpdatesMixin
) : BaseViewModel(), UpdatesProviderUi by updatesMixin {

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    val fiatSymbolFlow = combine(selectedFiat.flow(), getAvailableFiatCurrencies.flow()) { selectedFiat: String, fiatCurrencies: FiatCurrencies ->
        fiatCurrencies[selectedFiat]?.symbol
    }.onEach {
        sync()
    }

    private val fiatSymbolLiveData = fiatSymbolFlow.asLiveData()
    private val assetModelsLiveData = assetModelsFlow().asLiveData()
    val assetsWarningLiveData = assetWarningFlow().asLiveData()

    val balanceLiveData = mediateWith(
        assetModelsLiveData,
        fiatSymbolLiveData,
        tokenRatesUpdate,
        assetsUpdate,
        chainsUpdate
    ) { (assetModels: List<AssetModel>?, fiatSymbol: String?, tokenRatesUpdate: Set<String>?, assetsUpdate: Set<AssetKey>?, chainsUpdate: Set<String>?) ->
        val assetsWithState = assetModels?.map { asset ->
            val rateUpdate = tokenRatesUpdate?.let { asset.token.configuration.symbol in it }
            val balanceUpdate = assetsUpdate?.let { asset.primaryKey in it }
            val chainUpdate = chainsUpdate?.let { asset.token.configuration.chainId in it }
            val isTokenFiatChanged = when {
                fiatSymbol == null -> false
                asset.token.fiatSymbol == null -> false
                else -> fiatSymbol != asset.token.fiatSymbol
            }

            AssetWithStateModel(
                asset = asset,
                state = AssetUpdateState(rateUpdate, balanceUpdate, chainUpdate, isTokenFiatChanged)
            )
        }.orEmpty()

        BalanceModel(assetsWithState, fiatSymbol.orEmpty())
    }

    fun sync() {
        viewModelScope.launch {
            getAvailableFiatCurrencies.sync()

            val result = interactor.syncAssetsRates()

            result.exceptionOrNull()?.let(::showError)
            _hideRefreshEvent.value = Event(Unit)
        }
    }

    fun assetClicked(asset: AssetModel) {
        if (asset.isSupported.not()) {
            _showUnsupportedChainAlert.value = Event(Unit)
            return
        }

        val payload = AssetPayload(
            chainId = asset.token.configuration.chainId,
            chainAssetId = asset.token.configuration.id
        )

        router.openAssetDetails(payload)
    }

    fun avatarClicked() {
        router.openChangeAccountFromWallet()
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow(polkadotChainId)
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    private fun assetModelsFlow(): Flow<List<AssetModel>> =
        interactor.assetsFlow()
            .mapList {
                when {
                    !it.enabled -> null
                    !it.hasAccount -> null
                    else -> it.asset
                }
            }
            .map { it.filterNotNull() }
            .mapList { mapAssetToAssetModel(it) }

    private fun assetWarningFlow(): Flow<Boolean> =
        interactor.assetsFlow()
            .map { list ->
                list.any {
                    !it.hasAccount && !it.asset.markedNotNeed
                }
            }

    fun manageAssetsClicked() {
        router.openManageAssets()
    }

    fun onBalanceClicked() {
        viewModelScope.launch {
            val currencies = getAvailableFiatCurrencies()
            if (currencies.isEmpty()) return@launch
            val selected = selectedFiat.get()
            val selectedItem = currencies.first { it.id == selected }
            _showFiatChooser.value = FiatChooserEvent(DynamicListBottomSheet.Payload(currencies, selectedItem))
        }
    }

    fun onFiatSelected(item: FiatCurrency) {
        viewModelScope.launch {
            selectedFiat.set(item.id)
        }
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }
}
