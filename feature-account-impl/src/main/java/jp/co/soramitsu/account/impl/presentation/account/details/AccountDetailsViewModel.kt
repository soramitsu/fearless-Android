package jp.co.soramitsu.account.impl.presentation.account.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.account.api.presentation.exporting.buildChainAccountOptions
import jp.co.soramitsu.common.model.ImportAccountType
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.account.model.ConnectedAccountsInfoItem
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountDetailsViewModel @Inject constructor(
    private val interactor: AccountDetailsInteractor,
    private val walletInteractor: WalletInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val chainsRepository: ChainsRepository,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), AccountDetailsCallback, ExternalAccountActions by externalAccountActions {

    private val walletId = savedStateHandle.get<Long>(ACCOUNT_ID_KEY)!!

    private val walletItem = interactor.lightMetaAccountFlow(walletId)
        .map { wallet ->

            val icon = iconGenerator.createAddressIcon(
                wallet.supportedEcosystemWithIconAddress(),
                AddressIconGenerator.SIZE_BIG
            )

            val balanceModel = totalBalanceUseCase(walletId)

            WalletItemViewState(
                id = walletId,
                title = wallet.name,
                isSelected = false,
                walletIcon = icon,
                balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
                )
            )
        }

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val connectedAccountsSummaryFlow = interactor.getChainAccountsSummaryFlow(walletId)
        .mapList { (type, size) ->
            ConnectedAccountsInfoItem(
                type,
                mapTypeToTitle(type),
                size
            )
        }
        .map {
            it.sortedByDescending { it.amount }
        }
        .stateIn(this, SharingStarted.Eagerly, emptyList())

    override fun walletOptionsClicked(item: WalletItemViewState) {
        accountRouter.openOptionsWallet(item.id, false)
    }

    val state = MutableStateFlow(AccountDetailsState.Empty)

    init {
        subscribeScreenState()
    }

    private fun subscribeScreenState() {
        walletItem.onEach {
            state.value = state.value.copy(walletItem = it)
        }.launchIn(this)

        connectedAccountsSummaryFlow.onEach {
            state.value = state.value.copy(connectedAccountsInfo = it)
        }.launchIn(this)

    }

    override fun onBackClick() {
        accountRouter.back()
    }

    private fun mapTypeToTitle(type: ImportAccountType): String {
        val resId = when (type) {
            ImportAccountType.Substrate -> R.string.connected_accounts_substrate_title
            ImportAccountType.Ethereum -> R.string.connected_accounts_ethereum_title
            ImportAccountType.Ton -> R.string.connected_accounts_ton_title
        }
        return resourceManager.getString(resId)
    }

    fun exportClicked(chainId: ChainId) {
        viewModelScope.launch {
            val isEthereumBased = chainsRepository.getChain(chainId).isEthereumBased
            val hasChainAccount = interactor.getMetaAccount(walletId).hasChainAccount(chainId)
            val sources = if (hasChainAccount) {
                interactor.getChainAccountSecret(walletId, chainId).buildChainAccountOptions(isEthereumBased)
            } else {
                walletInteractor.getExportSourceTypes(chainId, walletId)
            }

            _showExportSourceChooser.value = Event(ExportSourceChooserPayload(chainId, sources))
        }
    }

    fun exportTypeSelected(selected: ExportSource, chainId: ChainId) {
        val destination = when (selected) {
            is ExportSource.Json -> accountRouter.openExportJsonPasswordDestination(walletId, chainId)
            is ExportSource.Seed -> accountRouter.getExportSeedDestination(walletId, chainId)
            is ExportSource.Mnemonic -> accountRouter.getExportMnemonicDestination(walletId, chainId)
        }

        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun accountsItemOptionsClicked(type: ImportAccountType) {
        launch {
            val connectedChainsSize = connectedAccountsSummaryFlow.value.firstOrNull { it.accountType == type }?.amount ?: 0

            if (connectedChainsSize > 0) {
                accountRouter.openEcosystemAccountsOptions(walletId, type)
            } else {
                accountRouter.openOptionsAddAccount(walletId, type)
            }
        }
    }

    fun switchNode(chainId: ChainId) {
        accountRouter.openNodes(chainId)
    }
}
