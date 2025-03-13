package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.androidfoundation.format.unsafeCast
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.io.NetworkStateListener
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.oauth.base.sdk.contract.OutwardsScreen
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardFlow
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.oauth.uiscreens.clientsui.GetSoraCardState
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.soracard.api.util.createSoraCardContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GetSoraCardViewModel @Inject constructor(
    private val interactor: SoraCardInteractor,
    private val router: SoraCardRouter,
    private val resourceManager: ResourceManager,
    private val networkStateListener: NetworkStateListener,
) : BaseViewModel(), GetSoraCardScreenInterface {

    private var currentSoraCardContractData: SoraCardContractData? = null

    private val _launchSoraCardRegistration = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardRegistration: LiveData<SoraCardContractData> = _launchSoraCardRegistration

    private val _state = MutableStateFlow(GetSoraCardState(applicationFee = "."))
    val state = _state.asStateFlow()

    init {
        interactor.basicStatus
            .combine(networkStateListener.subscribe()) { f1, f2 ->
                f1 to f2
            }
            .catch { it.localizedMessage?.let { it1 -> showError(it1) } }
            .onEach { (info, connection) ->
                info.availabilityInfo?.let {
                    currentSoraCardContractData = createSoraCardContract(
                        userAvailableXorAmount = it.xorBalance.toDouble(),
                        isEnoughXorAvailable = it.enoughXor,
                    )
                }
                _state.value = _state.value.copy(
                    connection = connection,
                    applicationFee = info.applicationFee.orEmpty(),
                    xorRatioAvailable = info.availabilityInfo?.xorRatioAvailable ?: false,
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        networkStateListener.release()
        super.onCleared()
    }

    fun handleSoraCardResult(soraCardResult: SoraCardResult) {
        when (soraCardResult) {
            is SoraCardResult.NavigateTo -> {
                when (soraCardResult.screen) {
                    OutwardsScreen.DEPOSIT -> {}
                    OutwardsScreen.SWAP -> {
                        viewModelScope.launch {
                            val xorId = interactor.xorAssetFlow().first().token.configuration.id
                            router.openSwapTokensScreen(
                                chainId = interactor.soraCardChainId,
                                assetIdFrom = null,
                                assetIdTo = xorId,
                            )
                        }
                    }

                    OutwardsScreen.BUY -> router.showBuyCrypto()
                }
            }

            is SoraCardResult.Success -> {
                viewModelScope.launch {
                    interactor.setStatus(soraCardResult.status)
                }.invokeOnCompletion {
                    router.back()
                }
            }

            is SoraCardResult.Failure -> {
                viewModelScope.launch {
                    interactor.setStatus(soraCardResult.status)
                }.invokeOnCompletion {
                    router.back()
                }
            }

            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Logout -> {
                viewModelScope.launch {
                    interactor.setLogout()
                }.invokeOnCompletion {
                    router.back()
                }
            }
        }
    }

    override fun onLogIn() {
        currentSoraCardContractData?.let {
            _launchSoraCardRegistration.value = it.copy(
                flow = it.flow.unsafeCast<SoraCardFlow.SoraCardKycFlow>().copy(logIn = true)
            )
        }
    }

    override fun onBack() {
        router.back()
    }

    override fun onSeeBlacklist() {
        router.openWebViewer(
            title = resourceManager.getString(R.string.sora_card_blacklisted_countires_title),
            url = "https://soracard.com/blacklist/",
        )
    }

    override fun onSignUp() {
        currentSoraCardContractData?.let {
            _launchSoraCardRegistration.value = it.copy(
                flow = it.flow.unsafeCast<SoraCardFlow.SoraCardKycFlow>().copy(logIn = false)
            )
        }
    }
}
