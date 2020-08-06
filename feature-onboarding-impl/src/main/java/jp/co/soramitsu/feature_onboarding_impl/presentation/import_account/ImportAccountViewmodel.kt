package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.model.EncryptionType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model.EncryptionTypeChooserDialogData
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model.SourceTypeChooserDialogData

class ImportAccountViewmodel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter
) : BaseViewModel() {

    private val _advancedVisibilityLiveData = MutableLiveData<Boolean>()
    val advancedVisibilityLiveData: LiveData<Boolean> = _advancedVisibilityLiveData

    private val _sourceTypeChooserDialogInitialData = MutableLiveData<SourceTypeChooserDialogData>()
    val sourceTypeChooserDialogInitialData: LiveData<SourceTypeChooserDialogData> = _sourceTypeChooserDialogInitialData

    private val _encryptionTypeChooserDialogInitialData = MutableLiveData<EncryptionTypeChooserDialogData>()
    val encryptionTypeChooserDialogInitialData: LiveData<EncryptionTypeChooserDialogData> = _encryptionTypeChooserDialogInitialData

    private val _selectedSourceTypeText = MutableLiveData<String>()
    val selectedSourceTypeText: LiveData<String> = _selectedSourceTypeText

    private val _selectedEncryptionTypeText = MutableLiveData<String>()
    val selectedEncryptionTypeText: LiveData<String> = _selectedEncryptionTypeText

    private var selectedSourceType: SourceType = SourceType.MNEMONIC_PASSPHRASE
    private var selectedEncryptionType: EncryptionType = EncryptionType.SR25519

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun advancedButtonClicked() {
        _advancedVisibilityLiveData.value = _advancedVisibilityLiveData.value?.not() ?: true
    }

    fun sourceTypeInputClicked() {
        disposables.add(
            interactor.getSourceTypes()
                .map { mapSourceTypesToSourceTypeDialogData(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _sourceTypeChooserDialogInitialData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun sourceTypeChanged(it: SourceType) {
        selectedSourceType = it
        _selectedSourceTypeText.value =  when(selectedSourceType) {
            SourceType.MNEMONIC_PASSPHRASE -> "Mnemonic passphrase"
            SourceType.RAW_SEED -> "Raw seed"
            SourceType.KEYSTORE -> "Keystore"
        }

        _advancedVisibilityLiveData.value = false
    }

    fun encryptionTypeInputClicked() {
        disposables.add(
            interactor.getEncryptionTypes()
                .map { mapEncryptionTypesToEncryptionTypeDialogData(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _encryptionTypeChooserDialogInitialData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun encryptionTypeChanged(it: EncryptionType) {
        selectedEncryptionType = it

        _selectedEncryptionTypeText.value =  when(selectedEncryptionType) {
            EncryptionType.SR25519 -> "Schnorrkel | sr25519 (recommended) "
            EncryptionType.ED25519 -> "Edwards | ed25519 (alternative)"
            EncryptionType.ECDSA -> "ECDSA | (BTC/ETH compatible)"
        }
    }

    private fun mapSourceTypesToSourceTypeDialogData(sourceTypes: List<SourceType>): SourceTypeChooserDialogData {
        return SourceTypeChooserDialogData(selectedSourceType, sourceTypes)
    }

    private fun mapEncryptionTypesToEncryptionTypeDialogData(encryptionTypes: List<EncryptionType>): EncryptionTypeChooserDialogData {
        return EncryptionTypeChooserDialogData(selectedEncryptionType, encryptionTypes)
    }
}