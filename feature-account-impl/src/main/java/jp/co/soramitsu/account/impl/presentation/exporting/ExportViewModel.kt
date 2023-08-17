package jp.co.soramitsu.account.impl.presentation.exporting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

abstract class ExportViewModel(
    protected val accountInteractor: AccountInteractor,
    protected val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    protected val metaId: Long,
    private val chainId: ChainId,
    val isExportFromWallet: Boolean = false,
    val exportSource: ExportSource
) : BaseViewModel() {
    private val _exportEvent = MutableLiveData<Event<String>>()
    val exportEvent: LiveData<Event<String>> = _exportEvent

    val accountLiveData = liveData { emit(loadAccount()) }
    val chainSecretLiveData = liveData { emit(loadSecrets(chainId)) }
    val isChainAccountLiveData = liveData { emit(accountInteractor.getMetaAccount(metaId).hasChainAccount(chainId)) }

    val secretLiveData = liveData { emit(loadSecrets()) }
    val chainLiveData = liveData { emit(loadChain()) }
    val isEthereum = chainLiveData.map { it.isEthereumBased }

    val cryptoTypeLiveData = chainLiveData.switchMap { chain ->
        accountLiveData.map { it.cryptoType(chain) }
    }

    private val _showSecurityWarningEvent = MutableLiveData<Event<Unit>>()
    val showSecurityWarningEvent = _showSecurityWarningEvent

    protected fun showSecurityWarning() {
        _showSecurityWarningEvent.sendEvent()
    }

    protected fun exportText(text: String) {
        _exportEvent.value = Event(text)
    }

    open fun securityWarningCancel() {
        // optional override
    }

    private suspend fun loadAccount() = accountInteractor.getMetaAccount(metaId)

    private suspend fun loadChain() = chainRegistry.getChain(chainId)

    private suspend fun loadSecrets() = accountInteractor.getMetaAccountSecrets(metaId)

    private suspend fun loadSecrets(chainId: ChainId) = accountInteractor.getChainAccountSecrets(metaId, chainId)
}
