package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.MainToolbarViewStateWithFilters
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverDappViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val resourceManager: ResourceManager,
    private val tonConnectInteractor: TonConnectInteractor,
) : BaseViewModel(), DiscoverDappScreenInterface {

    private val _seeAllBottomSheetState: MutableStateFlow<DappsListState?> = MutableStateFlow(null)
    val dappsListBottomSheetState: Flow<DappsListState?> = _seeAllBottomSheetState

    private val accountAddressToChainIdMap = mutableMapOf<String, ChainId?>()

    private val chainsFlow = chainInteractor.getChainsFlow().mapList {
        it.toChainItemState()
    }.inBackground()

    private val selectedChainId = MutableStateFlow<ChainId?>(null)

    private val selectedChainItemFlow =
        combine(selectedChainId, chainsFlow) { selectedChainId, chains ->
            selectedChainId?.let {
                chains.firstOrNull { it.id == selectedChainId }
            }
        }

    private val currentMetaAccountFlow = interactor.selectedLightMetaAccountFlow()

    private val dappListTypeSelectorState = MutableStateFlow(
        MultiToggleButtonState(
            currentSelection = DappListType.Discover,
            toggleStates = DappListType.entries
        )
    )

    private val dappsFlow = flowOf {
        tonConnectInteractor.getDappsConfig()
    }

    private val connectedDapps: Flow<DappConfig> = tonConnectInteractor.getConnectedDappsFlow(ConnectionSource.WEB)

    private val dAppsItemsFlow: Flow<List<DappConfig>> = combine(
        selectedChainId,
        dappListTypeSelectorState,
        dappsFlow,
        connectedDapps
    ) { selectedChainId, selectorState, dApps, connected ->
        when (selectorState.currentSelection) {
            DappListType.Discover -> dApps.map {
                it.copy(apps = it.apps.take(3))
            }
            DappListType.Connected -> listOf(connected)
        }
    }

    val toolbarState = MutableStateFlow(MainToolbarViewStateWithFilters.default)
    val state = MutableStateFlow(DiscoverDappState.default)

    init {
        observeToolbarStates()
        subscribeScreenState()

        router.chainSelectorPayloadFlow.map { chainId ->
            val walletId = interactor.getSelectedMetaAccount().id
            interactor.saveChainId(walletId, chainId)
            selectedChainId.value = chainId
        }.launchIn(this)

        selectedChainId.onEach { chainId ->
            BalanceUpdateTrigger.invoke(chainId = chainId)
        }.launchIn(this)

        interactor.selectedLightMetaAccountFlow().map { wallet ->
            selectedChainId.value = interactor.getSavedChainId(wallet.id)
        }.launchIn(this)
    }

    private fun subscribeScreenState() {
        dAppsItemsFlow.onEach {
            state.value = state.value.copy(dapps = it)
        }.launchIn(this)

        dappListTypeSelectorState.onEach {
            state.value = state.value.copy(multiToggleButtonState = it)
        }.launchIn(this)
    }

    private fun observeToolbarStates() {
        currentAddressModelFlow().onEach { addressModel ->
            toolbarState.update { prevState ->
                val newWalletIconState = when (prevState.homeIconState) {
                    is ToolbarHomeIconState.Navigation -> ToolbarHomeIconState.Wallet(walletIcon = addressModel.image)
                    is ToolbarHomeIconState.Wallet -> (prevState.homeIconState as ToolbarHomeIconState.Wallet).copy(walletIcon = addressModel.image)
                }
                prevState.copy(
                    title = addressModel.nameOrAddress,
                    homeIconState = newWalletIconState,
                )
            }
        }.launchIn(viewModelScope)

        combine(
            interactor.observeSelectedAccountChainSelectFilter(),
            selectedChainItemFlow
        ) { filter, chain ->
            toolbarState.update { prevState ->
                prevState.copy(
                    selectorViewState = ChainSelectorViewStateWithFilters(
                        selectedChainName = chain?.title,
                        selectedChainId = chain?.id,
                        selectedChainImageUrl = chain?.imageUrl,
                        filterApplied = filter
                    )
                )
            }
        }.launchIn(viewModelScope)

        nomisScoreInteractor.observeCurrentAccountScore()
            .onEach { score ->
                toolbarState.update { prevState ->
                    val newWalletIconState = (prevState.homeIconState as? ToolbarHomeIconState.Wallet)?.copy(score = score?.score)
                    newWalletIconState?.let {
                        prevState.copy(homeIconState = newWalletIconState)
                    } ?: prevState
                }
            }
            .launchIn(viewModelScope)
    }

    @Suppress("MagicNumber")
    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedLightMetaAccountFlow()
            .onEach { account ->
                val tonAddress = account.tonPublicKey?.toHexString(withPrefix = false)
                if (accountAddressToChainIdMap.containsKey(tonAddress).not()) {
                    selectedChainId.value = null
                    accountAddressToChainIdMap[tonAddress.orEmpty()] = null
                } else {
                    selectedChainId.value =
                        accountAddressToChainIdMap.getOrDefault(tonAddress, null)
                }
            }
            .map { addressIconGenerator.createAddressModel(it.supportedEcosystemWithIconAddress(), 40, it.name) }
    }

    override fun onButtonToggleChanged(type: DappListType) {
        dappListTypeSelectorState.update { prevState ->
            prevState.copy(currentSelection = type)
        }
    }

    override fun onSeeAllClick(type: String) {
        viewModelScope.launch {
            val dapps = dappsFlow.firstOrNull()?.find { it.type == type }?.apps ?: return@launch

            _seeAllBottomSheetState.update { prevState ->
                if (prevState != null) return@update prevState
                DappsListState(type.replaceFirstChar { it.uppercaseChar() }, dapps)
            }
        }
    }

    override fun onDappLongClick(dappId: String) {
        viewModelScope.launch {
            tonConnectInteractor.disconnect(dappId)
            showMessage("dApp disconnected")
        }
    }

    override fun onDappClick(dappId: String) {
        viewModelScope.launch {
            val remoteDappGroupsDeferred = async { dappsFlow.firstOrNull() }
            val connectedDappsDeferred = async { connectedDapps.firstOrNull() }

            val remoteDApps = remoteDappGroupsDeferred.await()?.flatMap { it.apps }
            val connectedDapps = connectedDappsDeferred.await()?.apps

            val selectedDapp = connectedDapps?.firstOrNull {
                it.identifier == dappId
            } ?: remoteDApps?.firstOrNull {
                it.identifier == dappId
            }

            if (selectedDapp?.name != null && selectedDapp.url != null) {
                router.openDappScreen(selectedDapp)
            }
        }
    }

    fun openWalletSelector() {
        // decided to turn it off
//        router.openSelectWallet()
    }

    fun openSearch() {
        viewModelScope.launch {
            val dapps = when (dappListTypeSelectorState.value.currentSelection) {
                DappListType.Discover -> {
                    dappsFlow.firstOrNull()?.flatMap { it.apps } ?: return@launch
                }
                DappListType.Connected -> {
                    connectedDapps.firstOrNull()?.apps ?: return@launch
                }
            }

            _seeAllBottomSheetState.update { prevState ->
                if (prevState != null) return@update prevState
                DappsListState(resourceManager.getString(R.string.common_search), dapps)
            }
        }
    }

    fun openSelectChain() {
        router.openSelectChain(selectedChainId.value, isFilteringEnabled = true)
    }

    fun onScoreClick() {
        viewModelScope.launch {
            val currentAccount = currentMetaAccountFlow.first()
            router.openScoreDetailsScreen(currentAccount.id)
        }
    }

    override fun bottomSheetDappSelected(dappId: String) {
        _seeAllBottomSheetState.update { null }
        onDappClick(dappId)
    }

    override fun onBottomSheetDappClose() {
        _seeAllBottomSheetState.update { null }
    }
}
