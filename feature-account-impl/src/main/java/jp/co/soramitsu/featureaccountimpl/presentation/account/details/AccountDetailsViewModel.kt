package jp.co.soramitsu.featureaccountimpl.presentation.account.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.featureaccountapi.domain.model.hasChainAccount
import jp.co.soramitsu.featureaccountapi.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.featureaccountapi.presentation.exporting.ExportSource
import jp.co.soramitsu.featureaccountapi.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.featureaccountapi.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.featureaccountimpl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.featureaccountimpl.domain.account.details.AccountInChain
import jp.co.soramitsu.featureaccountimpl.presentation.AccountRouter
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val UPDATE_NAME_INTERVAL_SECONDS = 1L

@HiltViewModel
class AccountDetailsViewModel @Inject constructor(
    private val interactor: AccountDetailsInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val assetNotNeedAccount: AssetNotNeedAccountUseCase,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val metaId = savedStateHandle.get<Long>(ACCOUNT_ID_KEY)!!

    private val _showAddAccountChooser = MutableLiveData<Event<AddAccountBottomSheet.Payload>>()
    val showAddAccountChooser: LiveData<Event<AddAccountBottomSheet.Payload>> = _showAddAccountChooser

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val _showImportChainAccountChooser = MutableLiveData<Event<ImportChainAccountsPayload>>()
    val showImportChainAccountChooser: LiveData<Event<ImportChainAccountsPayload>> = _showImportChainAccountChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    val accountNameFlow: MutableStateFlow<String> = MutableStateFlow("")

    val chainAccountProjections = interactor.getChainProjectionsFlow(metaId)
        .map { groupedList ->
            groupedList.mapKeys { (from, _) -> mapFromToTextHeader(from) }
                .mapValues { (_, accounts) -> accounts.map { mapChainAccountProjectionToUi(it) } }
                .toListWithHeaders()
        }
        .inBackground()
        .share()

    init {
        launch {
            accountNameFlow.emit(interactor.getMetaAccount(metaId).name)
        }

        syncNameChangesWithDb()
    }

    fun backClicked() {
        accountRouter.back()
    }

    @OptIn(FlowPreview::class)
    private fun syncNameChangesWithDb() {
        accountNameFlow
            .filter { it.isNotEmpty() }
            .debounce(UPDATE_NAME_INTERVAL_SECONDS.toDuration(DurationUnit.SECONDS))
            .onEach { interactor.updateName(metaId, it) }
            .launchIn(viewModelScope)
    }

    private fun mapFromToTextHeader(from: AccountInChain.From): TextHeader? {
        val resId = when (from) {
            AccountInChain.From.META_ACCOUNT -> R.string.default_account_shared_secret
            AccountInChain.From.CHAIN_ACCOUNT -> R.string.account_unique_secret
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
            val hasChainAccount = interactor.getMetaAccount(metaId).hasChainAccount(chainId)
            val sources = when {
                hasChainAccount -> interactor.getChainAccountSecret(metaId, chainId).buildExportSourceTypes(isEthereumBased)
                else -> interactor.getMetaAccountSecrets(metaId).buildExportSourceTypes(isEthereumBased)
            }

            _showExportSourceChooser.value = Event(ExportSourceChooserPayload(chainId, sources))
        }
    }

    fun showImportChainAccountChooser(chainId: ChainId) {
        viewModelScope.launch {
            val name = chainRegistry.getChain(chainId).name
            _showImportChainAccountChooser.postValue(Event(ImportChainAccountsPayload(chainId, metaId, name)))
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
            is ExportSource.Json -> accountRouter.openExportJsonPassword(metaId, chainId)
            is ExportSource.Seed -> accountRouter.openExportSeed(metaId, chainId)
            is ExportSource.Mnemonic -> accountRouter.openExportMnemonic(metaId, chainId)
        }

        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    fun chainAccountOptionsClicked(item: AccountInChainUi) = launch {
        if (item.hasAccount) {
            val supportedExplorers = chainRegistry.getChain(item.chainId).explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, item.address)
            externalAccountActions.showExternalActions(ExternalAccountActions.Payload(item.address, item.chainId, item.chainName, supportedExplorers))
        } else {
            _showAddAccountChooser.value = Event(
                AddAccountBottomSheet.Payload(
                    metaId = metaId,
                    chainId = item.chainId,
                    chainName = item.chainName,
                    symbol = chainRegistry.getChain(item.chainId).utilityAsset.symbol,
                    markedAsNotNeed = item.markedAsNotNeed
                )
            )
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

    fun noNeedAccount(chainId: ChainId, metaId: Long, symbol: String) {
        launch {
            assetNotNeedAccount.markNotNeed(chainId = chainId, metaId = metaId, symbol = symbol)
        }
    }
}
