package jp.co.soramitsu.feature_account_impl.presentation.exporting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.feature_account_impl.data.mappers.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel

abstract class ExportViewModel(
    protected val accountInteractor: AccountInteractor,
    protected val accountAddress: String,
    protected val resourceManager: ResourceManager,
    val exportSource: ExportSource
) : BaseViewModel() {
    protected val securityTypeLiveData = liveData { emit(loadSecuritySource()) }

    private val _exportEvent = MutableLiveData<Event<String>>()
    val exportEvent: LiveData<Event<String>> = _exportEvent

    private val accountLiveData = liveData { emit(loadAccount()) }

    val cryptoTypeLiveData = accountLiveData.map { mapCryptoTypeToCryptoTypeModel(resourceManager, it.cryptoType) }

    val networkTypeLiveData = accountLiveData.map { mapNetworkTypeToNetworkModel(it.network.type) }

    private val _showSecurityWarningEvent = MutableLiveData<Event<Unit>>()
    val showSecurityWarningEvent = _showSecurityWarningEvent

    protected fun showSecurityWarning() {
        _showSecurityWarningEvent.sendEvent()
    }

    protected fun exportText(text: String) {
        _exportEvent.value = Event(text)
    }

    open fun securityWarningConfirmed() {
        // optional override
    }

    private suspend fun loadAccount(): Account {
        return accountInteractor.getAccount(accountAddress)
    }

    private suspend fun loadSecuritySource(): SecuritySource {
        return accountInteractor.getSecuritySource(accountAddress)
    }
}