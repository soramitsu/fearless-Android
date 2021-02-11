package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.account.mnemonicViewer.MnemonicWordModel
import jp.co.soramitsu.common.account.mnemonicViewer.mapMnemonicToMnemonicWords
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload.CreateExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val accountName: String,
    private val selectedNetworkType: Node.NetworkType,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin {

    val mnemonicLiveData = liveData {
        emit(generateMnemonic())
    }

    private val _showInfoEvent = MutableLiveData<Event<Unit>>()
    val showInfoEvent: LiveData<Event<Unit>> = _showInfoEvent

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
    }

    fun infoClicked() {
        _showInfoEvent.value = Event(Unit)
    }

    fun nextClicked(derivationPath: String) {
        val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: return

        val mnemonicWords = mnemonicLiveData.value ?: return

        val mnemonic = mnemonicWords.map(MnemonicWordModel::word)

        val payload = ConfirmMnemonicPayload(
            mnemonic,
            CreateExtras(
                accountName,
                cryptoTypeModel.cryptoType,
                selectedNetworkType,
                derivationPath
            )
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