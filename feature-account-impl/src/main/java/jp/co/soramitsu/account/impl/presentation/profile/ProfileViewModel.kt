package jp.co.soramitsu.account.impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.SettingsItemAction
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AVATAR_SIZE_DP = 32

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    accountDetailsInteractor: AccountDetailsInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val router: AccountRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    getTotalBalance: TotalBalanceUseCase,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    resourceManager: ResourceManager
) : BaseViewModel(), ProfileScreenInterface, ExternalAccountActions by externalAccountActions {

    private val selectedAccountFlow: SharedFlow<MetaAccount> =
        interactor.selectedMetaAccountFlow().shareIn(viewModelScope, SharingStarted.Eagerly)

    private val accountIconFlow = selectedAccountFlow.map { wallet ->
        addressIconGenerator.createAddressIcon(
            wallet.supportedEcosystemWithIconAddress(),
            AddressIconGenerator.SIZE_BIG
        )
    }

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    private val defaultWalletItemViewState = WalletItemViewState(
        id = 0,
        balance = null,
        assetSymbol = null,
        changeBalanceViewState = null,
        title = "",
        walletIcon = resourceManager.getDrawable(R.drawable.ic_wallet),
        isSelected = false,
        additionalMetadata = "",
        score = null
    )

    val state: MutableStateFlow<ProfileScreenState> = MutableStateFlow(
        ProfileScreenState(
            walletState = defaultWalletItemViewState,
            walletsItemAction = SettingsItemAction.Transition,
            currency = selectedFiat.get(),
            language = "",
            nomisChecked = true,
        )
    )

    init {
        combine(getTotalBalance.observe(), selectedFiat.flow()) { balance, fiat ->
            val selectedFiatSymbol = getAvailableFiatCurrencies[fiat]?.symbol
            val formattedBalance =
                balance.balance.formatFiat(selectedFiatSymbol ?: balance.fiatSymbol)

            state.update { prevState ->
                val newWalletState = prevState.walletState.copy(
                    balance = formattedBalance,
                    changeBalanceViewState = ChangeBalanceViewState(
                        percentChange = balance.rateChange?.formatAsChange().orEmpty(),
                        fiatChange = balance.balanceChange.abs().formatFiat(balance.fiatSymbol)
                    )
                )
                prevState.copy(
                    walletState = newWalletState,
                    currency = fiat.uppercase(),
                )
            }
        }.launchIn(viewModelScope)

        selectedAccountFlow
            .onEach { account ->
                state.update { prevState ->
                    val newWalletState = prevState.walletState.copy(
                        id = account.id,
                        title = account.name,
                        supportedEcosystems = account.supportedEcosystems()
                    )
                    prevState.copy(walletState = newWalletState)
                }
            }.launchIn(viewModelScope)

        accountIconFlow.onEach { icon ->
            state.update { prevState ->
                val newWalletState = prevState.walletState.copy(
                    walletIcon = icon
                )
                prevState.copy(walletState = newWalletState)
            }

        }.launchIn(viewModelScope)

        nomisScoreInteractor.observeNomisMultichainScoreEnabled()
            .onEach {
                state.update { prev ->
                    prev.copy(nomisChecked = it)
                }
            }
            .launchIn(viewModelScope)

        nomisScoreInteractor.observeCurrentAccountScore().onEach {
            state.update { prevState ->
                val newWalletState = prevState.walletState.copy(
                    score = it?.score
                )
                prevState.copy(walletState = newWalletState)
            }
        }.launchIn(viewModelScope)

        accountDetailsInteractor.hasChainsWithNoAccount()
            .onEach { hasChainsWithNoAccount ->
                state.update { prevState ->
                    prevState.copy(
                        walletsItemAction =
                        if (hasChainsWithNoAccount)
                            SettingsItemAction.TransitionWithIcon(R.drawable.ic_status_warning_16)
                        else
                            SettingsItemAction.Transition
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val language = interactor.getSelectedLanguage()
            val mapped = mapLanguageToLanguageModel(language)
            state.update { prevState ->
                prevState.copy(language = mapped.displayName)
            }
        }
    }

    override fun aboutClicked() {
        router.openAboutScreen()
    }

    override fun onWalletOptionsClick(item: WalletItemViewState) {
        router.openAccountDetails(item.id)
    }

    override fun walletsClicked() {
        router.openSelectWallet()
    }

    override fun languagesClicked() {
        router.openLanguages()
    }

    override fun changePinCodeClicked() {
        router.openChangePinCode()
    }

    fun beaconQrScanned(qrContent: String) {
        router.openBeacon(qrContent)
    }

    override fun crowdloansClicked() {
        router.openCrowdloansScreen()
    }

    override fun currencyClicked() {
        viewModelScope.launch {
            val currencies = getAvailableFiatCurrencies()
            if (currencies.isEmpty()) return@launch
            val selected = selectedFiat.get()
            val selectedItem = currencies.first { it.id == selected }
            _showFiatChooser.value =
                FiatChooserEvent(DynamicListBottomSheet.Payload(currencies, selectedItem))
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

    override fun polkaswapDisclaimerClicked() {
        router.openPolkaswapDisclaimerFromProfile()
    }

    override fun onWalletConnectClick() {
        router.openConnectionsScreen()
    }

    override fun onTonConnectClick() {
        router.openTonConnectionsScreen()
    }

    override fun onNomisMultichainScoreContainerClick() {
        nomisScoreInteractor.nomisMultichainScoreEnabled =
            !nomisScoreInteractor.nomisMultichainScoreEnabled
    }

    override fun onScoreClick(item: WalletItemViewState) {
        router.openScoreDetailsScreen(item.id)
    }
}
