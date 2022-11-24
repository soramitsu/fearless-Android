package jp.co.soramitsu.wallet.impl.presentation.history

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AddressHistoryViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    val sharedState: SendSharedState,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter
) : BaseViewModel(), AddressHistoryScreenInterface {

    companion object {
        private const val RECENT_SIZE = 10
    }

    val chainId: ChainId = savedStateHandle[AddressHistoryFragment.KEY_PAYLOAD] ?: error("ChainId not specified")

    val state: StateFlow<LoadingState<AddressHistoryViewState>> = combine(
        walletInteractor.getOperationAddressWithChainIdFlow(RECENT_SIZE, chainId),
        walletInteractor.observeAddressBook(chainId)
    ) { recentAddressesInfo, addressBook ->
        val recentAddresses: Set<Address> = recentAddressesInfo.map { address ->
            val placeholder = resourceManager.getDrawable(R.drawable.ic_wallet)
            val chain = walletInteractor.getChain(chainId)
            val accountImage = address.ifEmpty { null }?.let {
                addressIconGenerator.createAddressIcon(chain.isEthereumBased, address, AddressIconGenerator.SIZE_BIG)
            }

            Address(
                name = addressBook.firstOrNull { it.address == address }?.name.orEmpty(),
                address = address,
                image = accountImage ?: placeholder,
                chainId = chainId,
                isSavedToContacts = address in addressBook.map { it.address }
            )
        }.toSet()

        val addressBookAddresses = addressBook.map { contact ->
            val placeholder = resourceManager.getDrawable(R.drawable.ic_wallet)
            val chain = walletInteractor.getChain(contact.chainId)
            val accountImage = contact.address.ifEmpty { null }?.let {
                addressIconGenerator.createAddressIcon(chain.isEthereumBased, contact.address, AddressIconGenerator.SIZE_BIG)
            }
            Address(
                name = contact.name.orEmpty(),
                address = contact.address,
                image = accountImage ?: placeholder,
                chainId = contact.chainId,
                isSavedToContacts = true
            )
        }.groupBy {
            it.name.firstOrNull()?.uppercase()
        }

        LoadingState.Loaded(
            AddressHistoryViewState(
                recentAddresses = recentAddresses,
                addressBookAddresses = addressBookAddresses
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    override fun onAddressClick(address: Address) {
        sharedState.updateAddress(address.address)
        router.back()
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onCreateContactClick(addressChainId: ChainId?, address: String?) {
        router.openCreateContact(addressChainId ?: chainId, address)
    }
}
