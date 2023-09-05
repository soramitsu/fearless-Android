package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.oauth.base.sdk.contract.OutwardsScreen
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.soracard.impl.presentation.createSoraCardContract
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.AssetPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class GetSoraCardViewModel @Inject constructor(
    private val interactor: SoraCardInteractor,
    private val router: SoraCardRouter,
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel(), GetSoraCardScreenInterface {

    private val _launchSoraCardRegistration = MutableLiveData<Event<SoraCardContractData>>()
    val launchSoraCardRegistration: LiveData<Event<SoraCardContractData>> = _launchSoraCardRegistration

    private var currentSoraCardContractData: SoraCardContractData? = null
    val state = MutableStateFlow(GetSoraCardState())

    init {
        interactor.subscribeToSoraCardAvailabilityFlow()
            .onEach {
                currentSoraCardContractData = createSoraCardContract(
                    userAvailableXorAmount = it.xorBalance.toDouble(),
                    isEnoughXorAvailable = it.enoughXor
                )
                state.value = state.value.copy(
                    xorBalance = it.xorBalance,
                    enoughXor = it.enoughXor,
                    percent = it.percent,
                    needInXor = it.needInXor,
                    needInEur = it.needInEur,
                    xorRatioAvailable = it.xorRatioAvailable
                )
            }.launchIn(viewModelScope)
    }

    override fun onEnableCard() {
        currentSoraCardContractData?.let { soraCardContractData ->
            _launchSoraCardRegistration.value = Event(soraCardContractData)
        }
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onGetMoreXor() {
        router.openGetMoreXor()
    }

    override fun onSeeBlacklist() {
        router.openWebViewer(
            title = resourceManager.getString(R.string.sora_card_blacklisted_countires_title),
            url = BuildConfig.SORA_CARD_BLACKLIST
        )
    }

    fun handleSoraCardResult(soraCardResult: SoraCardResult) {
        when (soraCardResult) {
            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Failure -> {
                interactor.setStatus(soraCardResult.status)
            }

            is SoraCardResult.Success -> {
                interactor.setStatus(soraCardResult.status)
            }

            is SoraCardResult.Logout -> {
                interactor.setLogout()
            }

            is SoraCardResult.NavigateTo -> {
                when (soraCardResult.screen) {
                    OutwardsScreen.DEPOSIT -> {
                        launch {
                            interactor.xorAssetFlow().firstOrNull()?.token?.configuration?.let {
                                val assetPayload = AssetPayload(it.chainId, it.id)
                                walletRouter.openReceive(assetPayload)
                            }
                        }
                    }

                    OutwardsScreen.SWAP -> {
                        launch {
                            interactor.xorAssetFlow().firstOrNull()?.let { xorAsset ->
                                router.openSwapTokensScreen(interactor.soraCardChainId, null, xorAsset.token.configuration.id)
                            }
                        }
                    }

                    OutwardsScreen.BUY -> router.showBuyCrypto()
                }
            }
        }
    }
}
