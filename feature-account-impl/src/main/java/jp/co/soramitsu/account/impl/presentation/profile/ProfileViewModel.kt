package jp.co.soramitsu.account.impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.account.api.presentation.account.withPortfolioBalance
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.SettingsItemAction
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    private var profileSource: TotalBalance = TotalBalance.AwaitingSelection
    private var latestScore: NomisScoreData? = null

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
        capturedProfileIconFlow(getTotalBalance.observe()) { wallet ->
            addressIconGenerator.createAddressIcon(
                wallet.supportedEcosystemWithIconAddress(),
                AddressIconGenerator.SIZE_BIG
            )
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(CapturedProfileIcon(TotalBalance.Unavailable, null))
        }.onEach { presentation ->
            profileSource = presentation.source
            val ready = presentation.source as? TotalBalance.Ready
            val wallet = defaultWalletItemViewState.copy(
                id = ready?.meta?.id ?: (presentation.source as? TotalBalance.Loading)?.metaId ?: 0,
                title = ready?.meta?.name.orEmpty(),
                walletIcon = presentation.icon ?: defaultWalletItemViewState.walletIcon,
                supportedEcosystems = ready?.meta?.supportedEcosystems().orEmpty(),
                score = latestScore?.takeIf { it.metaId == ready?.meta?.id }?.score
            ).withPortfolioBalance(presentation.source, resourceManager)
            state.update { it.copy(walletState = wallet) }
        }.launchIn(viewModelScope)

        selectedFiat.flow().onEach { fiat ->
            state.update { it.copy(currency = fiat.uppercase()) }
        }.launchIn(viewModelScope)

        nomisScoreInteractor.observeNomisMultichainScoreEnabled()
            .onEach {
                state.update { prev ->
                    prev.copy(nomisChecked = it)
                }
            }
            .launchIn(viewModelScope)

        nomisScoreInteractor.observeCurrentAccountScore().onEach { score ->
            latestScore = score
            val ready = profileSource as? TotalBalance.Ready
            state.update { previous ->
                previous.copy(walletState = previous.walletState.copy(
                    score = score?.takeIf { it.metaId == ready?.meta?.id }?.score
                ))
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

    override fun recoveryClicked() {
        router.openRecoveryCenter()
    }

    override fun aboutClicked() {
        router.openAboutScreen()
    }

    override fun onWalletOptionsClick(item: WalletItemViewState) {
        val ready = profileSource as? TotalBalance.Ready ?: return
        if (item.id == ready.meta.id) router.openAccountDetails(item.id)
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
        if (profileSource !is TotalBalance.Ready) return
        val wallet = state.value.walletState
        if (wallet.supportedEcosystems.any { it == WalletEcosystem.Substrate || it == WalletEcosystem.Ethereum }) {
            router.openConnectionsScreen()
        } else {
            router.openOptionsAddAccount(wallet.id, WalletEcosystem.Substrate)
        }
    }

    override fun onTonConnectClick() {
        if (profileSource !is TotalBalance.Ready) return
        val wallet = state.value.walletState
        if (wallet.supportsTon) {
            router.openTonConnectionsScreen()
        } else {
            router.openOptionsAddAccount(wallet.id, WalletEcosystem.Ton)
        }
    }

    override fun onManageAssetsClick() {
        router.openManageAssets()
    }

    override fun onNomisMultichainScoreContainerClick() {
        nomisScoreInteractor.nomisMultichainScoreEnabled =
            !nomisScoreInteractor.nomisMultichainScoreEnabled
    }

    override fun onScoreClick(item: WalletItemViewState) {
        val ready = profileSource as? TotalBalance.Ready ?: return
        if (item.id == ready.meta.id) router.openScoreDetailsScreen(item.id)
    }

}
