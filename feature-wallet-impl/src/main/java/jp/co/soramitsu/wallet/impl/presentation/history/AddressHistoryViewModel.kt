package jp.co.soramitsu.wallet.impl.presentation.history

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddressHistoryViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    val sharedState: SendSharedState,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter
) : BaseViewModel(), AddressHistoryScreenInterface {

    companion object {
        private const val RECENT_SIZE = 10
    }

    val chainId: ChainId =
        savedStateHandle[AddressHistoryFragment.KEY_PAYLOAD] ?: error("ChainId not specified")
    val chain = viewModelScope.async { chainsRepository.getChain(chainId) }

    val state: MutableStateFlow<LoadingState<AddressHistoryViewState>> =
        MutableStateFlow(LoadingState.Loading())

    private val addressBookFlow =
        walletInteractor.observeAddressBook(chainId).map { LoadingState.Loaded(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    private val recentAddressesDeferred =
        async { walletInteractor.getOperationAddressWithChainId(chainId, RECENT_SIZE) }

    private val allAddressesFlow = addressBookFlow.map {
        val data = it.dataOrNull()?.map { contact -> contact.address } ?: return@map emptySet<String>()
        val recentAddresses = recentAddressesDeferred.await()
        (data + recentAddresses).toSet()
    }.distinctUntilChanged()

    private val imagesFlow: MutableStateFlow<Map<String, PictureDrawable?>> = MutableStateFlow(emptyMap())

    init {
        observeAddressBook()
        observeImages()
        observeScore()
    }

    private fun observeAddressBook() {
        val placeholder = resourceManager.getDrawable(R.drawable.ic_wallet)
        addressBookFlow
            .mapNotNull { it.dataOrNull() }
            .onEach { addressBook ->
                    state.update { prevState ->
                        when (prevState) {
                            is LoadingState.Loading -> {
                                val newContacts = addressBook.map { contact ->
                                    Address(
                                        name = contact.name.orEmpty(),
                                        address = contact.address.trim(),
                                        image = imagesFlow.value[contact.address] ?: placeholder,
                                        chainId = contact.chainId,
                                        isSavedToContacts = true,
                                        getCachedNomisScore(contact.address)
                                    )
                                }.groupBy {
                                    it.name.firstOrNull()?.uppercase()
                                }
                                LoadingState.Loaded(AddressHistoryViewState(emptySet(), newContacts))
                            }

                            is LoadingState.Loaded -> {
                                val allPrevAddresses =
                                    prevState.dataOrNull()?.addressBookAddresses?.values?.flatten()
                                        ?: return@update prevState
                                val newContacts = addressBook.map { contact ->
                                    val existingContact =
                                        allPrevAddresses.find { it.address == contact.address }

                                    existingContact?.copy(
                                        name = contact.name.orEmpty(),
                                        address = contact.address.trim(),
                                        image = imagesFlow.value[contact.address] ?: placeholder,
                                        chainId = contact.chainId,
                                        isSavedToContacts = true,
                                    ) ?: Address(
                                        name = contact.name.orEmpty(),
                                        address = contact.address.trim(),
                                        image = imagesFlow.value[contact.address] ?: placeholder,
                                        chainId = contact.chainId,
                                        isSavedToContacts = true,
                                        prevState.data.recentAddresses.find { it.address == contact.address }?.score ?: getCachedNomisScore(contact.address)
                                    )
                                }.groupBy {
                                    it.name.firstOrNull()?.uppercase()
                                }

                                LoadingState.Loaded(prevState.data.copy(addressBookAddresses = newContacts))
                            }

                            else -> prevState
                        }
                    }
            }
            .onEach { addressBook ->
                val recentAddressesInfo = recentAddressesDeferred.await()
                state.update { prevState ->
                    val data = prevState.dataOrNull() ?: return@update prevState
                    val newRecentAddressModels = recentAddressesInfo.map { address ->
                        val existing = data.recentAddresses.find { it.address == address }
                        existing?.copy(name = addressBook.firstOrNull { it.address == address }?.name.orEmpty(),
                            address = address.trim(),
                            chainId = chainId,
                            isSavedToContacts = address in addressBook.map { it.address })
                            ?: Address(
                                name = addressBook.firstOrNull { it.address == address }?.name.orEmpty(),
                                address = address.trim(),
                                image = imagesFlow.value[address] ?: placeholder,
                                chainId = chainId,
                                isSavedToContacts = address in addressBook.map { it.address },
                                score = getCachedNomisScore(address)
                            )
                    }.toSet()
                    LoadingState.Loaded(data.copy(recentAddresses = newRecentAddressModels))
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun getCachedNomisScore(address: String): Int? {
        return if(chain.await().let { it.isEthereumChain || it.isEthereumBased }) {
            nomisScoreInteractor.getNomisScoreFromMemoryCache(address)?.score ?: NomisScoreData.LOADING_CODE
        } else {
            null
        }
    }

    private fun observeImages() {
        allAddressesFlow.onEach { allAddresses ->
            val chain = chain.await()
            coroutineScope {
                allAddresses.forEach { address ->
                    viewModelScope.launch {
                        val accountImage = address.ifEmpty { null }?.let {
                            addressIconGenerator.createAddressIcon(
                                chain.isEthereumBased,
                                address,
                                AddressIconGenerator.SIZE_BIG
                            )
                        }
                        imagesFlow.update { prevState ->
                            val newMap = prevState.toMutableMap()
                            newMap[address] = accountImage
                            newMap
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)

        imagesFlow.onEach { images ->
            val placeholder = resourceManager.getDrawable(R.drawable.ic_wallet)
            state.update { prevState ->
                val data = prevState.dataOrNull()?: return@update prevState
                val addressBook = data.addressBookAddresses.mapValues { group ->
                    group.value.map {
                        it.copy(image = images[it.address] ?: placeholder)
                    }
                }
                val recentAddresses = data.recentAddresses.map { addressModel ->
                    addressModel.copy(image = images[addressModel.address] ?: placeholder)
                }.toSet()

                LoadingState.Loaded(data.copy(recentAddresses = recentAddresses, addressBookAddresses = addressBook))
            }
        }.launchIn(viewModelScope)
    }

    private fun observeScore() {
        allAddressesFlow.onEach { allAddresses ->
            if(!chain.await().let { it.isEthereumChain || it.isEthereumBased }) return@onEach
            coroutineScope {
                allAddresses.forEach { address ->
                    val score = nomisScoreInteractor.getNomisScore(address)?.score ?: NomisScoreData.ERROR_CODE
                    state.update { prevState ->
                        if (prevState is LoadingState.Loaded) {
                            val addressBookWithScores =
                                prevState.data.addressBookAddresses.mapValues { group ->
                                    group.value.map {
                                        if (it.address == address) {
                                            it.copy(score = score)
                                        } else {
                                            it
                                        }
                                    }
                                }
                            val recentAddressesWithScores = prevState.data.recentAddresses.map {
                                if (it.address == address) {
                                    it.copy(score = score)
                                } else {
                                    it
                                }
                            }.toSet()

                            LoadingState.Loaded(
                                prevState.data.copy(
                                    addressBookAddresses = addressBookWithScores,
                                    recentAddresses = recentAddressesWithScores
                                )
                            )
                        } else {
                            prevState
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    override fun onAddressClick(address: Address) {
        sharedState.updateAddress(address.address)
        router.backWithResult(AddressHistoryFragment.RESULT_ADDRESS to address.address)
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onCreateContactClick(addressChainId: ChainId?, address: String?) {
        router.openCreateContact(addressChainId ?: chainId, address)
    }
}
