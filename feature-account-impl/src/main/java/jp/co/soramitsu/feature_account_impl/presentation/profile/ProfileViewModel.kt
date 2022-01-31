package jp.co.soramitsu.feature_account_impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountChosenNavDirection
import jp.co.soramitsu.feature_account_impl.presentation.language.mapper.mapLanguageToLanguageModel
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private const val AVATAR_SIZE_DP = 32

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    getTotalBalance: GetTotalBalanceUseCase
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    val totalBalanceLiveData = getTotalBalance().map(BigDecimal::formatAsCurrency).asLiveData()

    val selectedAccountLiveData: LiveData<MetaAccount> = interactor.selectedMetaAccountFlow().asLiveData()

    val accountIconLiveData: LiveData<AddressModel> = interactor.polkadotAddressForSelectedAccountFlow()
        .map { createIcon(it) }
        .asLiveData()
    private val _scanBeaconQrEvent = MutableLiveData<Event<Unit>>()
    val scanBeaconQrEvent: LiveData<Event<Unit>> = _scanBeaconQrEvent


    val selectedLanguageLiveData = liveData {
        val language = interactor.getSelectedLanguage()

        emit(mapLanguageToLanguageModel(language))
    }

    fun aboutClicked() {
        router.openAboutScreen()
    }

    fun walletsClicked() {
        router.openWallets(AccountChosenNavDirection.MAIN)
    }

    fun languagesClicked() {
        router.openLanguages()
    }

    fun changePinCodeClicked() {
        router.openChangePinCode()
    }

    fun accountActionsClicked() {
        val account = selectedAccountLiveData.value ?: return
        router.openAccountDetails(account.id)
    }

    private suspend fun createIcon(accountAddress: String): AddressModel {
        return addressIconGenerator.createAddressModel(accountAddress, AVATAR_SIZE_DP)
    }

    fun beaconClicked() {
        _scanBeaconQrEvent.sendEvent()
    }

    fun beaconQrScanned(qrContent: String) {
        router.openBeacon(qrContent)
    }
}
