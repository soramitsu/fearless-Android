package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.importing.importAccountType
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload.CreateExtras
import jp.co.soramitsu.feature_account_impl.presentation.view.mnemonic.MnemonicWordModel
import jp.co.soramitsu.feature_account_impl.presentation.view.mnemonic.mapMnemonicToMnemonicWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val payload: BackupMnemonicPayload,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin {

    val mnemonicLiveData = liveData {
        emit(generateMnemonic())
    }

    val chainAccountImportType = liveData {
        payload.chainAccountData?.chainId?.let {
            emit(interactor.getChain(it).importAccountType)
        }
    }

    private val substrateDerivationPathRegex = Regex("(//?[^/]+)*(///[^/]+)?")

    private val _showInfoEvent = MutableLiveData<Event<Unit>>()
    val showInfoEvent: LiveData<Event<Unit>> = _showInfoEvent

    private val _showInvalidSubstrateDerivationPathError = MutableLiveData<Event<Unit>>()
    val showInvalidSubstrateDerivationPathError: LiveData<Event<Unit>> = _showInvalidSubstrateDerivationPathError

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
    }

    fun infoClicked() {
        _showInfoEvent.value = Event(Unit)
    }

    fun nextClicked(substrateDerivationPath: String, ethereumDerivationPath: String) {
        val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: return

        val mnemonicWords = mnemonicLiveData.value ?: return

        val mnemonic = mnemonicWords.map(MnemonicWordModel::word)

        val isSubstrateDerivationPathValid = substrateDerivationPath.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid.not()) {
            _showInvalidSubstrateDerivationPathError.value = Event(Unit)
            return
        }

        val createExtras = when (payload.chainAccountData) {
            null -> CreateExtras(
                payload.accountName,
                cryptoTypeModel.cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath
            )
            else -> ConfirmMnemonicPayload.CreateChainExtras(
                payload.accountName,
                cryptoTypeModel.cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath,
                payload.chainAccountData.chainId,
                payload.chainAccountData.metaId
            )
        }
        val payload = ConfirmMnemonicPayload(
            mnemonic,
            createExtras
        )

        router.openConfirmMnemonicOnCreate(payload)
    }

    private suspend fun generateMnemonic(): List<MnemonicWordModel> {
        val mnemonic = interactor.generateMnemonic()

        return withContext(Dispatchers.Default) {
            mapMnemonicToMnemonicWords(mnemonic)
        }
    }
}
