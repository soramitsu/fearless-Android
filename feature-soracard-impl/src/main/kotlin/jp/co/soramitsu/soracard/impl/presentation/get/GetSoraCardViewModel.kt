package jp.co.soramitsu.soracard.impl.presentation.get

import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.greaterThen
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class GetSoraCardViewModel @Inject constructor(
    private val interactor: SoraCardInteractor,
    private val router: SoraCardRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel(), GetSoraCardScreenInterface {
    private companion object {
        val KYC_REAL_REQUIRED_BALANCE = BigDecimal(95)
        val KYC_REQUIRED_BALANCE_WITH_BACKLASH = BigDecimal(100)
    }

    val state = MutableStateFlow(GetSoraCardState())

    init {
        subscribeXorBalance()
        subscribeSoraCardInfo()
    }

    private fun subscribeXorBalance() {
        launch {
            interactor.xorAssetFlow()
                .distinctUntilChanged { old, new ->
                    old.transferable == new.transferable &&
                        old.token.configuration.priceId == new.token.configuration.priceId &&
                        old.token.configuration.precision == new.token.configuration.precision
                }
                .onEach {
                    val transferable = it.transferable
                    try {
                        val xorEurPrice = interactor.getXorEuroPrice(it.token.configuration.priceId) ?: error("XOR price not found")

                        val defaultScale = it.token.configuration.precision
                        val xorRequiredBalanceWithBacklash = KYC_REQUIRED_BALANCE_WITH_BACKLASH.divide(xorEurPrice, defaultScale, RoundingMode.HALF_EVEN)
                        val xorRealRequiredBalance = KYC_REAL_REQUIRED_BALANCE.divide(xorEurPrice, defaultScale, RoundingMode.HALF_EVEN)
                        val xorBalanceInEur = transferable.multiply(xorEurPrice)

                        val needInXor = if (transferable.greaterThen(xorRealRequiredBalance)) {
                            BigDecimal.ZERO
                        } else {
                            xorRequiredBalanceWithBacklash.minus(transferable)
                        }

                        val needInEur = if (xorBalanceInEur.greaterThen(KYC_REAL_REQUIRED_BALANCE)) {
                            BigDecimal.ZERO
                        } else {
                            KYC_REQUIRED_BALANCE_WITH_BACKLASH.minus(xorBalanceInEur)
                        }

                        state.value = state.value.copy(
                            xorBalance = transferable,
                            enoughXor = transferable.greaterThen(xorRealRequiredBalance),
                            percent = transferable.divide(xorRealRequiredBalance, defaultScale, RoundingMode.HALF_EVEN),
                            needInXor = needInXor,
                            needInEur = needInEur,
                            xorRatioUnavailable = false
                        )
                    } catch (e: Exception) {
                        state.value = state.value.copy(
                            xorBalance = transferable,
                            enoughXor = false,
                            xorRatioUnavailable = true
                        )
                    }
                }
                .launchIn(this)
        }
    }

    private fun subscribeSoraCardInfo() {
        launch {
            interactor.subscribeSoraCardInfo()
                .distinctUntilChanged()
                .collectLatest {
                    state.value = state.value.copy(soraCardInfo = it)
                }
        }
    }

    override fun onEnableCard() {
    }

    override fun onAlreadyHaveCard() {
    }

    override fun onNavigationClick() {
        router.back()
    }

    fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        launch {
            interactor.updateSoraCardInfo(
                accessToken,
                refreshToken,
                accessTokenExpirationTime,
                kycStatus
            )
        }
    }

    override fun onGetMoreXor() {
        router.openGetMoreXor()
    }

    override fun onSeeBlacklist(url: String) {
        router.openWebViewer(
            title = resourceManager.getString(R.string.sora_card_blacklisted_countires_title),
            url = url
        )
    }
}
