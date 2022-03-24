package jp.co.soramitsu.feature_account_impl.presentation.account.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.domain.GetAppVersion
import jp.co.soramitsu.common.domain.isAppVersionSupported
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.feature_account_api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val UPDATE_NAME_INTERVAL_SECONDS = 1L

class AccountDetailsViewModel(
    private val interactor: AccountDetailsInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    private val metaId: Long,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val getAppVersion: GetAppVersion
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val _showImportChainAccountChooser = MutableLiveData<Event<ImportChainAccountsPayload>>()
    val showImportChainAccountChooser: LiveData<Event<ImportChainAccountsPayload>> = _showImportChainAccountChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    val accountNameFlow: MutableStateFlow<String> = MutableStateFlow("")

    private val metaAccount = async(Dispatchers.Default) { interactor.getMetaAccount(metaId) }

    val chainAccountProjections = flowOf { interactor.getChainProjections(metaAccount()) }
        .map { groupedList ->
            groupedList.mapKeys { (from, _) -> mapFromToTextHeader(from) }
                .mapValues { (_, accounts) -> accounts.map { mapChainAccountProjectionToUi(it) } }
                .toListWithHeaders()
        }
        .inBackground()
        .share()

    private val appVersion: AppVersion = getAppVersion()

    init {
        launch {
            accountNameFlow.emit(metaAccount().name)
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

    private fun mapFromToTextHeader(from: AccountInChain.From): TextHeader {
        val resId = when (from) {
            AccountInChain.From.META_ACCOUNT -> R.string.default_account_shared_secret
            AccountInChain.From.CHAIN_ACCOUNT -> R.string.account_unique_secret
        }

        return TextHeader(resourceManager.getString(resId))
    }

    private suspend fun mapChainAccountProjectionToUi(accountInChain: AccountInChain) = with(accountInChain) {
        val address = projection?.address ?: resourceManager.getString(R.string.account_no_chain_projection)
        val accountIcon = projection?.let {
            iconGenerator.createAddressIcon(it.accountId, AddressIconGenerator.SIZE_SMALL, backgroundColorRes = R.color.account_icon_dark)
        } ?: resourceManager.getDrawable(R.drawable.ic_warning_filled)

        AccountInChainUi(
            chainId = chain.id,
            chainName = chain.name,
            chainIcon = chain.icon,
            address = address,
            accountIcon = accountIcon,
            accountName = accountInChain.name,
            accountFrom = accountInChain.from,
            isSupported = isAppVersionSupported(accountInChain.chain.minSupportedVersion, appVersion)
        )
    }

    fun exportClicked(chainId: ChainId) {
        viewModelScope.launch {
            val isEthereumBased = chainRegistry.getChain(chainId).isEthereumBased
            val sources = interactor.getMetaAccountSecrets(metaId).buildExportSourceTypes(isEthereumBased)
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
        val supportedExplorers = chainRegistry.getChain(item.chainId).explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, item.address)
        externalAccountActions.showExternalActions(ExternalAccountActions.Payload(item.address, item.chainId, item.chainName, supportedExplorers))
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
}
