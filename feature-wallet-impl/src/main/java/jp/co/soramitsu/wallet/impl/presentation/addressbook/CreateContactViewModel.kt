package jp.co.soramitsu.wallet.impl.presentation.addressbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.AddressNotValidException
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainItemState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CreateContactViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter
) : BaseViewModel(), CreateContactScreenInterface {

    val address: String? = savedStateHandle[CreateContactFragment.KEY_PAYLOAD]
    val chainId: String? = savedStateHandle[CreateContactFragment.KEY_CHAIN_ID]

    private val nameInputFlow = MutableStateFlow("")
    private val addressInputFlow = MutableStateFlow(address.orEmpty())

    private val chainIdFlow = MutableStateFlow(chainId)

    private val selectedChainItem: Flow<ChainItemState?> = chainIdFlow.mapNotNull { chainId ->
        chainId?.let {
            val chain = walletInteractor.getChain(it)
            ChainItemState(
                id = chain.id,
                imageUrl = chain.icon,
                title = chain.name,
                isSelected = false,
                tokenSymbols = chain.assets.associate { it.id to it.symbolToShow }
            )
        }
    }

    private val chainSelectorStateFlow = selectedChainItem.map {
        SelectorState(
            title = resourceManager.getString(R.string.common_network),
            subTitle = it?.title,
            iconUrl = it?.imageUrl
        )
    }.stateIn(this, SharingStarted.Eagerly, SelectorState.default)

    val state: StateFlow<CreateContactViewState> = combine(
        chainSelectorStateFlow,
        nameInputFlow,
        addressInputFlow
    ) { chainSelectorState, name, address ->
        val createContactEnabled = name.isNotBlank() && address.isNotBlank()
        CreateContactViewState(chainSelectorState, name, address, createContactEnabled)
    }.stateIn(this, SharingStarted.Eagerly, CreateContactViewState.default)

    init {
        viewModelScope.launch {
            router.chainSelectorPayloadFlow.collect { chainId ->
                chainId?.let {
                    chainIdFlow.value = it
                }
            }
        }
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onCreateContactClick() {
        val selectedChainId = chainIdFlow.value ?: return
        val name = state.value.contactNameInput
        val address = state.value.contactAddressInput
        launch {
            val isValid = walletInteractor.validateSendAddress(selectedChainId, address)
            if (isValid) {
                walletInteractor.saveAddress(name, address, selectedChainId)
                router.back()
            } else {
                showError(AddressNotValidException(resourceManager))
            }
        }
    }

    override fun onChainClick() {
        val selectedChainId = chainIdFlow.value
        router.openSelectChain(selectedChainId = selectedChainId, showAllChains = false)
    }

    override fun onNameInput(input: String) {
        nameInputFlow.value = input
    }

    override fun onAddressInput(input: String) {
        addressInputFlow.value = input
    }
}
