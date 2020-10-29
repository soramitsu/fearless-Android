package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.mnemonicViewer.MnemonicWordModel
import jp.co.soramitsu.common.account.mnemonicViewer.mapMnemonicToMnemonicWords
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload.*

class BackupMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val accountName: String,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val networkChooserMixin: NetworkChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin,
    NetworkChooserMixin by networkChooserMixin {

    val mnemonicLiveData = generateMnemonic().asLiveData()

    private val _showInfoEvent = MutableLiveData<Event<Unit>>()
    val showInfoEvent: LiveData<Event<Unit>> = _showInfoEvent

    init {
        disposables += networkDisposable
        disposables += cryptoDisposable
    }

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
    }

    fun infoClicked() {
        _showInfoEvent.value = Event(Unit)
    }

    fun nextClicked(derivationPath: String) {
        val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: return
        val selectedNetwork = selectedNetworkLiveData.value ?: return
        val mnemonicWords = mnemonicLiveData.value ?: return

        val mnemonic = mnemonicWords.map(MnemonicWordModel::word)

        val payload = ConfirmMnemonicPayload(
            mnemonic,
            CreateExtras(
                accountName,
                cryptoTypeModel.cryptoType,
                selectedNetwork.networkTypeUI.networkType,
                derivationPath
            )
        )

        router.openConfirmMnemonicOnCreate(payload)
    }

    private fun generateMnemonic(): Single<List<MnemonicWordModel>> {
        return interactor.generateMnemonic()
            .subscribeOn(Schedulers.io())
            .map(::mapMnemonicToMnemonicWords)
            .observeOn(AndroidSchedulers.mainThread())
    }
}