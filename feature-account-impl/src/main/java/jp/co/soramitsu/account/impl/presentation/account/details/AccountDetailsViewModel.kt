package jp.co.soramitsu.account.impl.presentation.account.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.account.api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val UPDATE_NAME_INTERVAL_SECONDS = 1L

@HiltViewModel
class AccountDetailsViewModel @Inject constructor(
    private val interactor: AccountDetailsInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val assetNotNeedAccount: AssetNotNeedAccountUseCase,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), AccountDetailsCallback, ExternalAccountActions by externalAccountActions {

    private val walletId = savedStateHandle.get<Long>(ACCOUNT_ID_KEY)!!
    private val wallet = flowOf {
        interactor.getMetaAccount(walletId)
    }
    private val walletItem = wallet
        .map { wallet ->

            val icon = iconGenerator.createAddressIcon(
                wallet.substrateAccountId,
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

    private val _showImportChainAccountChooser = MutableLiveData<Event<ImportChainAccountsPayload>>()
    val showImportChainAccountChooser: LiveData<Event<ImportChainAccountsPayload>> = _showImportChainAccountChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val enteredQueryFlow = MutableStateFlow("")
    val accountNameFlow = MutableStateFlow("")

    val chainAccountProjections = combine(
        interactor.getChainProjectionsFlow(walletId),
        enteredQueryFlow
    ) { groupedList, query ->
        groupedList.mapKeys { (from, _) -> mapFromToTextHeader(from) }
            .mapValues { (_, accounts) ->
                accounts.filter { it.chain.name.lowercase().contains(query.lowercase()) }
                    .map { mapChainAccountProjectionToUi(it) }
            }
            .filter { it.value.isNotEmpty() }
            .toListWithHeaders()
    }
        .inBackground()
        .share()

    val state = combine(
        walletItem,
        chainAccountProjections,
        enteredQueryFlow
    ) { walletItem, chainProjections, query ->
        AccountDetailsState(
            walletItem = walletItem,
            chainProjections = chainProjections,
            searchQuery = query
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccountDetailsState.Empty)

    init {
        launch {
            accountNameFlow.emit(interactor.getMetaAccount(walletId).name)
        }

        syncNameChangesWithDb()
    }

    override fun onBackClick() {
        accountRouter.back()
    }

    override fun onSearchInput(input: String) {
        enteredQueryFlow.value = input
    }

    @OptIn(FlowPreview::class)
    private fun syncNameChangesWithDb() {
        accountNameFlow
            .filter { it.isNotEmpty() }
            .debounce(UPDATE_NAME_INTERVAL_SECONDS.toDuration(DurationUnit.SECONDS))
            .onEach { interactor.updateName(walletId, it) }
            .launchIn(viewModelScope)
    }

    private fun mapFromToTextHeader(from: AccountInChain.From): TextHeader? {
        val resId = when (from) {
            AccountInChain.From.META_ACCOUNT -> R.string.default_account_shared_secret
            AccountInChain.From.CHAIN_ACCOUNT -> R.string.account_custom_secret
            AccountInChain.From.ACCOUNT_WO_ADDRESS -> return null
        }

        return TextHeader(resourceManager.getString(resId))
    }

    private suspend fun mapChainAccountProjectionToUi(accountInChain: AccountInChain) = with(accountInChain) {
        val address = projection?.address ?: resourceManager.getString(R.string.account_no_chain_projection)

        val accountIcon = when {
            projection != null -> iconGenerator.createAddressIcon(
                accountInChain.chain.isEthereumBased,
                projection.address,
                AddressIconGenerator.SIZE_SMALL,
                R.color.account_icon_dark
            )
            accountInChain.markedAsNotNeed -> null
            else -> resourceManager.getDrawable(R.drawable.ic_warning_filled)
        }

        AccountInChainUi(
            chainId = chain.id,
            chainName = chain.name,
            chainIcon = chain.icon,
            address = address,
            accountIcon = accountIcon,
            accountName = accountInChain.name,
            accountFrom = accountInChain.from,
            isSupported = accountInChain.chain.isSupported,
            hasAccount = accountInChain.hasAccount,
            markedAsNotNeed = accountInChain.markedAsNotNeed
        )
    }

    fun exportClicked(chainId: ChainId) {
        viewModelScope.launch {
            val isEthereumBased = chainRegistry.getChain(chainId).isEthereumBased
            val hasChainAccount = interactor.getMetaAccount(walletId).hasChainAccount(chainId)
            val sources = when {
                hasChainAccount -> interactor.getChainAccountSecret(walletId, chainId).buildExportSourceTypes(isEthereumBased)
                else -> interactor.getMetaAccountSecrets(walletId).buildExportSourceTypes(isEthereumBased)
            }

            _showExportSourceChooser.value = Event(ExportSourceChooserPayload(chainId, sources))
        }
    }

    fun showImportChainAccountChooser(chainId: ChainId) {
        viewModelScope.launch {
            val name = chainRegistry.getChain(chainId).name
            _showImportChainAccountChooser.postValue(Event(ImportChainAccountsPayload(chainId, walletId, name)))
        }
    }

    fun createChainAccount(chainId: ChainId, metaId: Long) {
        viewModelScope.launch {
            accountRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = false)
        }
    }

    fun importChainAccount(chainId: ChainId, metaId: Long) {
        viewModelScope.launch {
            accountRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = true)
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

    override fun chainAccountOptionsClicked(item: AccountInChainUi) {
        launch {
            if (item.hasAccount) {
                val supportedExplorers =
                    chainRegistry.getChain(item.chainId).explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, item.address)
                externalAccountActions.showExternalActions(ExternalAccountActions.Payload(item.address, item.chainId, item.chainName, supportedExplorers))
            } else {
                val utilityAsset = chainRegistry.getChain(item.chainId).utilityAsset
                val payload = AddAccountBottomSheet.Payload(
                    metaId = walletId,
                    chainId = item.chainId,
                    chainName = item.chainName,
                    assetId = utilityAsset?.id.orEmpty(),
                    priceId = utilityAsset?.priceId,
                    markedAsNotNeed = item.markedAsNotNeed
                )
                accountRouter.openOptionsAddAccount(payload)
            }
        }
    }

    fun switchNode(chainId: ChainId) {
        accountRouter.openNodes(chainId)
    }

    fun chainAccountClicked(item: AccountInChainUi) {
        if (item.isSupported.not()) {
            _showUnsupportedChainAlert.value = Event(Unit)
        }
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    fun createAccount(chainId: ChainId, metaId: Long) {
        accountRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = false)
    }

    fun importAccount(chainId: ChainId, metaId: Long) {
        accountRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = true)
    }

    fun noNeedAccount(chainId: ChainId, metaId: Long, assetId: String, priceId: String?) {
        launch {
            assetNotNeedAccount.markNotNeed(chainId = chainId, metaId = metaId, assetId = assetId, priceId = priceId)
        }
    }
}
