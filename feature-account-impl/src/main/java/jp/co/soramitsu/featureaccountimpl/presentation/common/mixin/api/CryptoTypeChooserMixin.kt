package jp.co.soramitsu.featureaccountimpl.presentation.common.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.featureaccountimpl.presentation.view.advanced.encryption.model.CryptoTypeModel

interface CryptoTypeChooserMixin {

    val selectedEncryptionTypeLiveData: MutableLiveData<CryptoTypeModel>

    val encryptionTypeChooserEvent: LiveData<Event<Payload<CryptoTypeModel>>>

    fun chooseEncryptionClicked()
}
