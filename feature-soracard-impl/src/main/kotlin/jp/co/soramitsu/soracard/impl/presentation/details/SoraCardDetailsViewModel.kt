package jp.co.soramitsu.soracard.impl.presentation.details

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.intent.isAppAvailableCompat
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardDetailsCallback
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardDetailsScreenState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardIBANCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardMainSoraContentCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardMenuAction
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardSettingsCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardSettingsOption
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.soracard.api.util.SoraCardOptions
import jp.co.soramitsu.soracard.api.util.createSoraCardGateHubContract
import jp.co.soramitsu.soracard.api.util.readyToStartGatehubOnboarding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoraCardDetailsViewModel @Inject constructor(
    private val soraCardInteractor: SoraCardInteractor,
    private val clipboard: ClipboardManager,
    private val router: SoraCardRouter,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    private val _shareLinkEvent = SingleLiveEvent<String>()
    val shareLinkEvent: LiveData<String> = _shareLinkEvent

    val telegramChat = SingleLiveEvent<Unit>()
    val fiatWallet = SingleLiveEvent<String>()
    val fiatWalletMarket = SingleLiveEvent<String>()

    private var ibanCache: IbanInfo? = null

    private val _launchSoraCard = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCard: LiveData<SoraCardContractData> = _launchSoraCard

    private val _soraCardDetailsScreenState = MutableStateFlow(
        SoraCardDetailsScreenState(
            soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
                balance = null,
                phone = null,
                soraCardMenuActions = SoraCardMenuAction.entries,
                canStartGatehubFlow = false,
            ),
            soraCardSettingsCard = SoraCardSettingsCardState(
                soraCardSettingsOptions = SoraCardSettingsOption.entries,
                phone = "",
            ),
            soraCardIBANCardState = null,
            logoutDialog = false,
            fiatWalletDialog = false,
        )
    )
    val soraCardDetailsScreenState = _soraCardDetailsScreenState.asStateFlow()

    init {
        viewModelScope.launch {
                soraCardInteractor.basicStatus.value.let { basicStatus ->
                    val local = _soraCardDetailsScreenState.value
                    ibanCache = basicStatus.ibanInfo
                    val phoneFormatted = basicStatus.phone?.let { "+$it" }
                    _soraCardDetailsScreenState.value = local.copy(
                        soraCardIBANCardState = SoraCardIBANCardState(
                            iban = basicStatus.ibanInfo?.iban.orEmpty(),
                            closed = basicStatus.ibanInfo?.ibanStatus == IbanStatus.CLOSED,
                        ),
                        soraCardMainSoraContentCardState = local.soraCardMainSoraContentCardState.copy(
                            balance = basicStatus.ibanInfo?.balance,
                            phone = phoneFormatted,
                            canStartGatehubFlow = basicStatus.ibanInfo?.ibanStatus.readyToStartGatehubOnboarding(),
                        ),
                        soraCardSettingsCard = local.soraCardSettingsCard?.copy(
                            phone = phoneFormatted.orEmpty(),
                        ),
                    )
                }
            }
    }

    fun onShowSoraCardDetailsClick() {
        /* Functionality will be added in further releases */
    }

    fun onSoraCardMenuActionClick(position: Int) {
        /* Functionality will be added in further releases */
    }

    fun onReferralBannerClick() {
        /* Functionality will be added in further releases */
    }

    fun onCloseReferralBannerClick() {
        /* Functionality will be added in further releases */
    }

    fun onRecentActivityClick(position: Int) {
        /* Functionality will be added in further releases */
    }

    fun onShowMoreRecentActivitiesClick() {
        /* Functionality will be added in further releases */
    }

    fun onIbanCardShareClick() {
        ibanCache?.let {
            if (it.ibanStatus != IbanStatus.CLOSED && it.iban.isNotEmpty()) _shareLinkEvent.value = it.iban
        }
    }

    fun onIbanCardClick() {
        ibanCache?.let {
            if (it.ibanStatus != IbanStatus.CLOSED && it.iban.isNotEmpty()) {
                clipboard.addToClipboard(it.iban)
                showMessage(resourceManager.getString(R.string.common_copied))
            }
        }
    }

    /**
     * only clickable if IBAN is issued
     */
    fun onExchangeXorClick() {
        _launchSoraCard.value = createSoraCardGateHubContract()
    }

    fun onSettingsOptionClick(position: Int, context: Context?) {
        val settings = soraCardDetailsScreenState.value.soraCardSettingsCard
            ?.soraCardSettingsOptions ?: return

        when (settings[position]) {
            SoraCardSettingsOption.LOG_OUT ->
                _soraCardDetailsScreenState.value =
                    _soraCardDetailsScreenState.value.copy(logoutDialog = true)

            SoraCardSettingsOption.SUPPORT_CHAT ->
                telegramChat.trigger()

            SoraCardSettingsOption.MANAGE_SORA_CARD -> {
                checkNotNull(context)
                val fiat = SoraCardOptions.soracardFiatPackageProd
                if (context.isAppAvailableCompat(fiat)) {
                    fiatWallet.value = fiat
                } else {
                    _soraCardDetailsScreenState.value =
                        _soraCardDetailsScreenState.value.copy(fiatWalletDialog = true)
                }
            }
        }
    }

    fun onLogoutDismiss() {
        _soraCardDetailsScreenState.value =
            _soraCardDetailsScreenState.value.copy(logoutDialog = false)
    }

    fun onFiatWalletDismiss() {
        _soraCardDetailsScreenState.value =
            _soraCardDetailsScreenState.value.copy(fiatWalletDialog = false)
    }

    fun onOpenFiatWalletMarket() {
        fiatWalletMarket.value = SoraCardOptions.soracardFiatPackageProd
    }

    fun onBack() {
        router.back()
    }

    fun onSoraCardLogOutClick() {
        viewModelScope.launch {
                soraCardInteractor.setLogout()
        }.invokeOnCompletion {
            if (it == null)
                router.back()
        }
    }
}