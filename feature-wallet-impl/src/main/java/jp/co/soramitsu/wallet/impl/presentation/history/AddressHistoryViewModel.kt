package jp.co.soramitsu.wallet.impl.presentation.history

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AddressHistoryViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter
) : BaseViewModel(), AddressHistoryScreenInterface {

    val chainId: ChainId = savedStateHandle[AddressHistoryFragment.KEY_PAYLOAD] ?: error("ChainId not specified")

    val state: StateFlow<AddressHistoryViewState> = flow {
        val contacts = walletInteractor.getContacts(
            chainId = chainId,
            query = ""
        )
        emit(
            AddressHistoryViewState(
                addressList = contacts.map {
                    val placeholder = resourceManager.getDrawable(R.drawable.ic_wallet)
                    val accountImage = if (it.isNotEmpty()) {
                        runCatching { it.fromHex() }.getOrNull()?.let { accountId ->
                            addressIconGenerator.createAddressIcon(accountId, AddressIconGenerator.SIZE_BIG)
                        }
                    } else {
                        null
                    }

                    Address(
                        name = "",
                        address = it,
                        image = accountImage ?: placeholder
                    )
                }.toSet()
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = AddressHistoryViewState(emptySet()))

    override fun onAddressClick(address: Address) {
        //
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onCreateContactClick(address: String?) {
        //
    }
}
