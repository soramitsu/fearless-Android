package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.updateFrom
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.data.mappers.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel

class CryptoTypeChooser(
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager
) : CryptoTypeChooserMixin {

    private val encryptionTypes = getCryptoTypeModels()

    override val selectedEncryptionTypeLiveData = mediatorLiveData<CryptoTypeModel> {
        updateFrom(preferredTypeLiveData())
    }

    private val _encryptionTypeChooserEvent = MutableLiveData<Event<Payload<CryptoTypeModel>>>()

    override val encryptionTypeChooserEvent: LiveData<Event<Payload<CryptoTypeModel>>> =
        _encryptionTypeChooserEvent

    override fun chooseEncryptionClicked() {
        val selectedType = selectedEncryptionTypeLiveData.value

        if (selectedType != null) {
            _encryptionTypeChooserEvent.value = Event(Payload(encryptionTypes, selectedType))
        }
    }

    private fun preferredTypeLiveData() = liveData {
        val cryptoType = interactor.getPreferredCryptoType()
        val mapped = mapCryptoTypeToCryptoTypeModel(resourceManager, cryptoType)

        emit(mapped)
    }

    private fun getCryptoTypeModels(): List<CryptoTypeModel> {
        val types = interactor.getCryptoTypes()

        return types.map { mapCryptoTypeToCryptoTypeModel(resourceManager, it) }
    }
}
