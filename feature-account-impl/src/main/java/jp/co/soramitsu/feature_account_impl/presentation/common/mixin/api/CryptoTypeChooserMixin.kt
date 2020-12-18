package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel

interface CryptoTypeChooserMixin {
    val cryptoDisposable: CompositeDisposable

    val selectedEncryptionTypeLiveData: MutableLiveData<CryptoTypeModel>

    val encryptionTypeChooserEvent: LiveData<Event<Payload<CryptoTypeModel>>>

    fun chooseEncryptionClicked()
}