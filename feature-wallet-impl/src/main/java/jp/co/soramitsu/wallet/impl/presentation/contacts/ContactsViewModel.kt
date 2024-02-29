package jp.co.soramitsu.wallet.impl.presentation.contacts

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    val sharedState: SendSharedState,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter
) : BaseViewModel(), ContactsScreenInterface {

    companion object {
        private const val RECENT_SIZE = 10
    }

    val chainId: ChainId =
        savedStateHandle[ContactsFragment.KEY_PAYLOAD] ?: error("ChainId not specified")

    val state: StateFlow<LoadingState<ContactsViewState>> =
        walletInteractor.observeAddressBook(chainId).map { addressBook ->
            val contactBookAddresses = addressBook.map { contact ->
                val placeholder = resourceManager.getDrawable(R.drawable.ic_wallet)
                val chain = walletInteractor.getChain(contact.chainId)
                val accountImage = contact.address.ifEmpty { null }?.let {
                    addressIconGenerator.createAddressIcon(
                        chain.isEthereumBased,
                        contact.address,
                        AddressIconGenerator.SIZE_BIG
                    )
                }
                Contact(
                    name = contact.name.orEmpty(),
                    address = contact.address.trim(),
                    image = accountImage ?: placeholder,
                    chainId = contact.chainId,
                    isSavedToContacts = true
                )
            }.groupBy {
                it.name.firstOrNull()?.uppercase()
            }

            LoadingState.Loaded(
                ContactsViewState(
                    contactBookAddresses = contactBookAddresses
                )
            )
        }.stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = LoadingState.Loading()
        )

    override fun onContactClick(contact: Contact) {
        sharedState.updateAddress(contact.address)
        router.backWithResult(ContactsFragment.RESULT_CONTACT to contact.address)
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onCreateContactClick(addressChainId: ChainId?, address: String?) {
        router.openCreateContact(addressChainId ?: chainId, address)
    }
}