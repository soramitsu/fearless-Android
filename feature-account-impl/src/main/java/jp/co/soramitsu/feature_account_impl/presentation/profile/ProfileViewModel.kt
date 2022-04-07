package jp.co.soramitsu.feature_account_impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.TotalBalance
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountChosenNavDirection
import jp.co.soramitsu.feature_account_impl.presentation.account.model.format
import jp.co.soramitsu.feature_account_impl.presentation.language.mapper.mapLanguageToLanguageModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val AVATAR_SIZE_DP = 32

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    getTotalBalance: GetTotalBalanceUseCase,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    val totalBalanceLiveData = getTotalBalance().map(TotalBalance::format).asLiveData()

    val selectedAccountLiveData: LiveData<MetaAccount> = interactor.selectedMetaAccountFlow().asLiveData()

    val accountIconLiveData: LiveData<AddressModel> = interactor.polkadotAddressForSelectedAccountFlow()
        .map { createIcon(it) }
        .asLiveData()

    val selectedLanguageLiveData = liveData {
        val language = interactor.getSelectedLanguage()

        emit(mapLanguageToLanguageModel(language))
    }

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    val selectedFiatLiveData: LiveData<String> = selectedFiat.flow().asLiveData().map { it.uppercase() }

    private val _openScanQrCodeLiveData = MutableLiveData<Event<Unit>>()
    val openScanQrCodeLiveData: LiveData<Event<Unit>> = _openScanQrCodeLiveData

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

    fun currencyClicked() {
        viewModelScope.launch {
            val currencies = getAvailableFiatCurrencies()
            if (currencies.isEmpty()) return@launch
            val selected = selectedFiat.get()
            val selectedItem = currencies.first { it.id == selected }
            _showFiatChooser.value = FiatChooserEvent(DynamicListBottomSheet.Payload(currencies, selectedItem))
        }
    }

    fun onFiatSelected(item: FiatCurrency) {
        viewModelScope.launch {
            selectedFiat.set(item.id)
        }
    }

    fun walletConnect() {
        _openScanQrCodeLiveData.value = Event(Unit)
    }
}
