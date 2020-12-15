package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.data.mappers.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel

class CryptoTypeChooser(
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager
) : CryptoTypeChooserMixin {
    override var cryptoDisposable: CompositeDisposable = CompositeDisposable()

    private val encryptionTypesLiveData = getCryptoTypeModels().asLiveData(cryptoDisposable)

    override val selectedEncryptionTypeLiveData = interactor.getPreferredCryptoType()
        .map { mapCryptoTypeToCryptoTypeModel(resourceManager, it) }
        .asMutableLiveData(cryptoDisposable)

    private val _encryptionTypeChooserEvent = MutableLiveData<Event<Payload<CryptoTypeModel>>>()

    override val encryptionTypeChooserEvent: LiveData<Event<Payload<CryptoTypeModel>>> =
        _encryptionTypeChooserEvent

    override fun chooseEncryptionClicked() {
        val encryptionTypes = encryptionTypesLiveData.value
        val selectedType = selectedEncryptionTypeLiveData.value

        if (encryptionTypes != null && selectedType != null) {
            _encryptionTypeChooserEvent.value = Event(Payload(encryptionTypes, selectedType))
        }
    }

    private fun getCryptoTypeModels(): Single<List<CryptoTypeModel>> {
        return interactor.getCryptoTypes()
            .subscribeOn(Schedulers.io())
            .map { list -> list.map { mapCryptoTypeToCryptoTypeModel(resourceManager, it) } }
            .observeOn(AndroidSchedulers.mainThread())
    }
}