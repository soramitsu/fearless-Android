package jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.feature_account_api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountInChainUi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AccountsForExportViewModel(
    private val interactor: AccountDetailsInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val payload: AccountsForExportPayload
) : BaseViewModel() {

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val metaAccount = async(Dispatchers.Default) { interactor.getMetaAccount(payload.metaId) }

    val chainAccountProjections = flowOf { interactor.getChainProjections(metaAccount()) }
        .map {
            it.filter {
                it.key == payload.from
            }
        }
        .map { groupedList ->
            groupedList.mapKeys { (from, _) -> mapFromToTextHeader(from) }
                .mapValues { (_, accounts) -> accounts.map { mapChainAccountProjectionToUi(it) } }
                .toListWithHeaders()
        }
        .inBackground()
        .share()

    fun backClicked() {
        accountRouter.back()
    }

    private fun mapFromToTextHeader(from: AccountInChain.From): TextHeader {
        val resId = when (from) {
            AccountInChain.From.META_ACCOUNT -> R.string.accounts_with_one_key
            AccountInChain.From.CHAIN_ACCOUNT -> R.string.accounts_with_changed_key
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
            enabled = false
        )
    }

    fun exportTypeSelected(selected: ExportSource, chainId: ChainId) {
        val destination = when (selected) {
            is ExportSource.Json -> accountRouter.openExportJsonPassword(payload.metaId, chainId)
            is ExportSource.Seed -> accountRouter.openExportSeed(payload.metaId, chainId)
            is ExportSource.Mnemonic -> accountRouter.openExportMnemonic(payload.metaId, chainId)
        }

        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    fun onExportClick(chainId: ChainId = polkadotChainId) {
        viewModelScope.launch {
            val sources = interactor.getMetaAccountSecrets(payload.metaId).buildExportSourceTypes(false)
            _showExportSourceChooser.value = Event(ExportSourceChooserPayload(chainId, sources))
        }
    }
}
