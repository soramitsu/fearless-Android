package jp.co.soramitsu.account.impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardInfo
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import jp.co.soramitsu.oauth.R as SoraCardR

private const val AVATAR_SIZE_DP = 32

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val walletInteractor: WalletInteractor,
    private val soraCardInteractor: SoraCardInteractor,
    private val router: AccountRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    getTotalBalance: GetTotalBalanceUseCase,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val _launchSoraCardSignIn = MutableLiveData<Event<SoraCardContractData>>()
    val launchSoraCardSignIn: LiveData<Event<SoraCardContractData>> = _launchSoraCardSignIn

    val totalBalanceLiveData = combine(getTotalBalance(), selectedFiat.flow()) { balance, fiat ->
        val selectedFiatSymbol = getAvailableFiatCurrencies[fiat]?.symbol
        balance.balance.formatFiat(selectedFiatSymbol ?: balance.fiatSymbol)
    }.asLiveData()

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

    val hasMissingAccountsFlow = walletInteractor.assetsFlow().map {
        it.any { it.hasAccount.not() }
    }.stateIn(this, SharingStarted.Eagerly, false)

//    private val soraCardState = soraCardInteractor.subscribeSoraCardInfo().map {
//        val kycStatus = it?.kycStatus?.let(::mapKycStatus)
//        SoraCardItemViewState(kycStatus, it, null, true)
//    }
    private val soraCardState = flowOf(SoraCardItemViewState())

    val hideZeroBalancesState: Flow<Boolean> = walletInteractor.observeHideZeroBalanceEnabledForCurrentWallet()

    fun aboutClicked() {
        router.openAboutScreen()
    }

    fun walletsClicked() {
        router.openSelectWallet()
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

    fun beaconQrScanned(qrContent: String) {
        router.openBeacon(qrContent)
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

    fun onExperimentalClicked() {
        router.openExperimentalFeatures()
    }

    fun polkaswapDisclaimerClicked() {
        router.openPolkaswapDisclaimer()
    }

    fun onSoraCardClicked() {
        launch {
            val soraCardState: SoraCardItemViewState? = soraCardState.firstOrNull()
            if (soraCardState?.kycStatus == null) {
                router.openGetSoraCard()
            } else {
                onSoraCardStatusClicked()
            }
        }
    }

    private fun onSoraCardStatusClicked() {
        launch {
            val soraCardState = soraCardState.firstOrNull()
            _launchSoraCardSignIn.value = Event(
                SoraCardContractData(
                    locale = Locale.ENGLISH,
                    apiKey = BuildConfig.SORA_CARD_API_KEY,
                    domain = BuildConfig.SORA_CARD_DOMAIN,
                    environment = when {
                        BuildConfig.DEBUG -> SoraCardEnvironmentType.TEST
                        else -> SoraCardEnvironmentType.PRODUCTION
                    },
                    soraCardInfo = soraCardState?.soraCardInfo?.let {
                        SoraCardInfo(
                            accessToken = it.accessToken,
                            refreshToken = it.refreshToken,
                            accessTokenExpirationTime = it.accessTokenExpirationTime
                        )
                    },
                    kycCredentials = SoraCardKycCredentials(
                        endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
                        username = BuildConfig.SORA_CARD_KYC_USERNAME,
                        password = BuildConfig.SORA_CARD_KYC_PASSWORD
                    ),
                    client = OptionsProvider.header
                )
            )
        }
    }

    private fun mapKycStatus(kycStatus: String): String? {
        return when (runCatching { SoraCardCommonVerification.valueOf(kycStatus) }.getOrNull()) {
            SoraCardCommonVerification.Pending -> {
                resourceManager.getString(SoraCardR.string.kyc_result_verification_in_progress)
            }
            SoraCardCommonVerification.Successful -> {
                resourceManager.getString(R.string.sora_card_verification_successful)
            }
            SoraCardCommonVerification.Rejected -> {
                resourceManager.getString(SoraCardR.string.verification_rejected_title)
            }
            SoraCardCommonVerification.Failed -> {
                resourceManager.getString(SoraCardR.string.verification_failed_title)
            }
            SoraCardCommonVerification.NoFreeAttempt -> {
                resourceManager.getString(SoraCardR.string.no_free_kyc_attempts_title)
            }
            else -> {
                null
            }
        }
    }

    fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        launch {
            soraCardInteractor.updateSoraCardInfo(
                accessToken,
                refreshToken,
                accessTokenExpirationTime,
                kycStatus
            )
        }
    }

    fun onHideZeroBalancesClick() {
        viewModelScope.launch {
            walletInteractor.toggleHideZeroBalancesForCurrentWallet()
        }
    }
}
