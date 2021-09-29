package jp.co.soramitsu.feature_account_impl.presentation.account.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.core.model.WithJson
import jp.co.soramitsu.core.model.WithMnemonic
import jp.co.soramitsu.core.model.WithSeed
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAccountModelToAccount
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAccountToAccountModel
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private const val UPDATE_NAME_INTERVAL_SECONDS = 1L

private const val ACCOUNT_ICON_SIZE_DP = 18

class AccountDetailsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val resourceManager: ResourceManager,
    val metaId: Long
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {
    private val accountNameChanges = MutableSharedFlow<String>()

    val accountLiveData = liveData {
        emit(getAccount(metaId))
    }

    val networkModel = accountLiveData.map { mapNetworkTypeToNetworkModel(it.network.type) }

    private val _showExportSourceChooser = MutableLiveData<Event<Payload<ExportSource>>>()
    val showExportSourceChooser: LiveData<Event<Payload<ExportSource>>> = _showExportSourceChooser

    init {
        observeNameChanges()
    }

    fun nameChanged(name: String) {
        viewModelScope.launch {
            accountNameChanges.emit(name)
        }
    }

    fun backClicked() {
        accountRouter.back()
    }

    private suspend fun getAccount(metaId: Long): AccountModel {
        val account = accountInteractor.getAccount(accountAddress)

        val icon = iconGenerator.createAddressIcon(accountAddress, ACCOUNT_ICON_SIZE_DP)

        return mapAccountToAccountModel(account, icon, resourceManager)
    }

    fun addressClicked() {
        accountLiveData.value?.let {
            val payload = ExternalAccountActions.Payload(it.address, it.network.type)

            externalAccountActions.showExternalActions(payload)
        }
    }

    fun exportClicked() {
        viewModelScope.launch {
            val sources = buildExportSourceTypes()

            _showExportSourceChooser.value = Event(Payload(sources))
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun observeNameChanges() {
        accountNameChanges
            .filter(::isNameChanged)
            .debounce(UPDATE_NAME_INTERVAL_SECONDS.seconds)
            .onEach { changeName(it) }
            .launchIn(viewModelScope)
    }

    private suspend fun changeName(newName: String) {
        val accountModel = accountLiveData.value!!

        accountInteractor.updateAccountName(mapAccountModelToAccount(accountModel), newName)
    }

    private fun isNameChanged(name: String): Boolean {
        val account = accountLiveData.value

        return account?.name != name
    }

    private suspend fun buildExportSourceTypes(): List<ExportSource> {
        val securitySource = accountInteractor.getSecuritySource(accountAddress)
        val options = mutableListOf<ExportSource>()

        if (securitySource is WithMnemonic) options += ExportSource.Mnemonic
        if (securitySource is WithSeed && securitySource.seed != null) options += ExportSource.Seed
        if (securitySource is WithJson) options += ExportSource.Json

        return options
    }

    fun exportTypeSelected(selected: ExportSource) {
        val destination = when (selected) {
            is ExportSource.Json -> accountRouter.openExportJsonPassword(accountAddress)
            is ExportSource.Seed -> accountRouter.openExportSeed(accountAddress)
            is ExportSource.Mnemonic -> accountRouter.openExportMnemonic(accountAddress)
        }

        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }
}
