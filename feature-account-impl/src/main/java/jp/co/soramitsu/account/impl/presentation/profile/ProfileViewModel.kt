package jp.co.soramitsu.account.impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.oauth.base.sdk.contract.OutwardsScreen
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.soracard.impl.presentation.createSoraCardContract
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.model.AssetPayload
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import jp.co.soramitsu.oauth.R as SoraCardR

private const val AVATAR_SIZE_DP = 32

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val walletInteractor: WalletInteractor,
    private val soraCardInteractor: SoraCardInteractor,
    private val soraCardRouter: SoraCardRouter,
    private val router: AccountRouter,
    private val walletRouter: WalletRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    getTotalBalance: TotalBalanceUseCase,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val _launchSoraCardSignIn = MutableLiveData<Event<SoraCardContractData>>()
    val launchSoraCardSignIn: LiveData<Event<SoraCardContractData>> = _launchSoraCardSignIn

    val totalBalanceLiveData = combine(getTotalBalance.observe(), selectedFiat.flow()) { balance, fiat ->
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

    private val soraCardState = soraCardInteractor.subscribeSoraCardStatus().map {
        println("!!! ProfileVM: subscribeSoraCardStatus = ${it.name}")
        val kycStatus = mapKycStatus(it)
        SoraCardItemViewState(kycStatus, true)
    }

    private var currentSoraCardContractData: SoraCardContractData? = null

    init {
        observeSoraCardAvailability()
    }

    private fun observeSoraCardAvailability() {
        soraCardInteractor.subscribeToSoraCardAvailabilityFlow().onEach {
            println("!!! ProfileVM: subscribeToSoraCardAvailabilityFlow = ${it.enoughXor}")
            currentSoraCardContractData = createSoraCardContract(
                userAvailableXorAmount = it.xorBalance.toDouble(),
                isEnoughXorAvailable = it.enoughXor
            )
        }.launchIn(viewModelScope)
    }

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
        router.openPolkaswapDisclaimerFromProfile()
    }

    fun onSoraCardClicked() {
        launch {
            val soraCardState: SoraCardItemViewState? = soraCardState.firstOrNull()
            if (soraCardState?.kycStatus == null) {
                router.openGetSoraCard()
            } else {
                currentSoraCardContractData?.let { contractData ->
                    _launchSoraCardSignIn.value = Event(contractData)
                }
            }
        }
    }

    private fun mapKycStatus(kycStatus: SoraCardCommonVerification): String? {
        return when (kycStatus) {
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

            else -> {
                null
            }
        }
    }

    fun handleSoraCardResult(soraCardResult: SoraCardResult) {
        when (soraCardResult) {
            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Failure -> {
                soraCardInteractor.setStatus(soraCardResult.status)
            }

            is SoraCardResult.Success -> {
                soraCardInteractor.setStatus(soraCardResult.status)
            }

            is SoraCardResult.Logout -> {
                soraCardInteractor.setLogout()
            }

            is SoraCardResult.NavigateTo -> {
                when (soraCardResult.screen) {
                    OutwardsScreen.DEPOSIT -> {
                        launch {
                            soraCardInteractor.xorAssetFlow().firstOrNull()?.token?.configuration?.let {
                                val assetPayload = AssetPayload(it.chainId, it.id)
                                walletRouter.openReceive(assetPayload)
                            }
                        }
                    }
                    OutwardsScreen.SWAP -> {
                        launch {
                            soraCardInteractor.xorAssetFlow().firstOrNull()?.token?.configuration?.let {
                                soraCardRouter.openSwapTokensScreen(it.chainId, null, it.id)
                            }
                        }
                    }
                    OutwardsScreen.BUY -> soraCardRouter.showBuyCrypto()
                }
            }
        }
    }

    fun onWalletConnectClick() {
        router.openConnectionsScreen()
    }
}
