package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.model.DappConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
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
    private val accountInteractor: AccountInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val resourceManager: ResourceManager,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
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
            // todo remove
//            .map { it ->
//                if (it.type == "featured") {
//                    it.copy(
//                        apps = listOf(
//                            DappModel(
//                                identifier = "some_id",
//                                chains = listOf("-3"),
//                                name = "Blueprint testnet",
//                                url = "https://ton-explorer.dev.sora2.tachi.soramitsu.co.jp",
//                                description = "injected FW dapp test example",
//                                background = null,
//                                icon = "https://i.imgur.com/wxkIEAE.png"
//                            )
//                        ).plus(it.apps)
//                    )
//                } else {
//                    it
//                }
//            }
    }
    private val connectedDapps: Flow<DappConfig> = tonConnectInteractor.getConnectedDapps()

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
                        filterApplied = ChainSelectorViewStateWithFilters.Filter.entries.find {
                            it.name == filter
                        } ?: ChainSelectorViewStateWithFilters.Filter.All
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

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedLightMetaAccountFlow()
            .map {
                val polkadotAddressPrefix = 0
                val address = it.substrateAccountId.toAddress(polkadotAddressPrefix.toShort())
                WalletAccount(address, it.name)
            }
            .catch { emit(WalletAccount("", "")) }
            .onEach { account ->
                if (accountAddressToChainIdMap.containsKey(account.address).not()) {
                    selectedChainId.value = null
                    accountAddressToChainIdMap[account.address] = null
                } else {
                    selectedChainId.value =
                        accountAddressToChainIdMap.getOrDefault(account.address, null)
                }
            }
            .map { generateAddressModel(it, 40) }
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
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
            val connectedDappsDeferred =  async { connectedDapps.firstOrNull() }
            val dapps = remoteDappGroupsDeferred.await()?.flatMap { it.apps }
                ?.plus(connectedDappsDeferred.await()?.apps ?: emptyList()) ?: return@launch
            val selectedDapp = dapps.firstOrNull { it.identifier == dappId }

            if(selectedDapp?.name != null && selectedDapp.url != null) {
                router.openDappScreen(selectedDapp)
            }
        }
    }

    fun openWalletSelector() {
        router.openSelectWallet()
    }

    fun openSearch() {
        viewModelScope.launch {
            val dapps = dappsFlow.firstOrNull()?.flatMap { it.apps } ?: return@launch

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