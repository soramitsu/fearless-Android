package jp.co.soramitsu.account.impl.presentation.exporting.json.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportJsonConfirmViewModel @Inject constructor(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    private val chainRegistry: ChainRegistry,
    private val savedStateHandle: SavedStateHandle
) : ExportViewModel(
    accountInteractor,
    resourceManager,
    chainRegistry,
    savedStateHandle.get<ExportJsonConfirmPayload>(ExportJsonConfirmFragment.PAYLOAD_KEY)!!.metaId,
    savedStateHandle.get<ExportJsonConfirmPayload>(ExportJsonConfirmFragment.PAYLOAD_KEY)!!.chainId,
    savedStateHandle.get<ExportJsonConfirmPayload>(ExportJsonConfirmFragment.PAYLOAD_KEY)!!.isExportWallet,
    ExportSource.Json
) {

    private val payload = savedStateHandle.get<ExportJsonConfirmPayload>(ExportJsonConfirmFragment.PAYLOAD_KEY)!!

    private val _shareEvent = MutableLiveData<Event<File>>()
    val shareEvent: LiveData<Event<File>> = _shareEvent

    private val _showJsonImportTypeEvent = MutableLiveData<Event<Boolean>>()
    val showJsonImportTypeEvent: LiveData<Event<Boolean>> = _showJsonImportTypeEvent

    val substrateJson = payload.substrateJson
    val ethereumJson = payload.ethereumJson

    fun changePasswordClicked() {
        back()
    }

    fun back() {
        router.back()
    }

    fun confirmSubstrateClicked() {
        _showJsonImportTypeEvent.value = Event(false)
    }

    fun confirmEthereumClicked() {
        _showJsonImportTypeEvent.value = Event(true)
    }

    fun shareCompleted() {
        router.finishExportFlow()
    }

    fun exportSubstrateAsFile() = viewModelScope.launch {
        substrateJson ?: return@launch
        val chain = chainLiveData.value ?: return@launch
        val address = accountLiveData.value?.address(chain) ?: return@launch
        val fileName = "$address.json"

        shareFile(fileName, substrateJson)
    }

    fun exportEthereumJsonAsFile() = viewModelScope.launch {
        ethereumJson ?: return@launch
        val chain = chainLiveData.value?.takeIf { it.isEthereumBased } ?: chainRegistry.getChain(moonriverChainId)
        val address = accountLiveData.value?.address(chain) ?: return@launch
        val fileName = "$address.json"

        shareFile(fileName, ethereumJson)
    }

    private suspend fun shareFile(fileName: String, json: String) {
        val result = accountInteractor.createFileInTempStorageAndRetrieveAsset(fileName)

        if (result.isSuccess) {
            val file = result.requireValue()

            file.writeText(json)

            _shareEvent.value = Event(file)
        } else {
            showError(result.requireException())
        }
    }

    fun onExportByText(isEthereum: Boolean) {
        val json = if (isEthereum) ethereumJson else substrateJson
        exportText(json ?: return)
    }

    fun onExportByFile(isEthereum: Boolean) {
        if (isEthereum) {
            exportEthereumJsonAsFile()
        } else {
            exportSubstrateAsFile()
        }
    }
}
