package jp.co.soramitsu.soracard.impl.domain

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.greaterThen
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.domain.SoraCardRepository
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardAvailabilityInfo
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.compareByTotal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class SoraCardInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val soraCardRepository: SoraCardRepository,
    private val soraCardClientProxy: SoraCardClientProxy
) : SoraCardInteractor {

    private val _soraCardStatus = MutableStateFlow(SoraCardCommonVerification.NotFound)

    override var soraCardChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun xorAssetFlow(): Flow<Asset> = combine(
        chainRegistry.chainFlow(soraCardChainId),
        accountRepository.selectedMetaAccountFlow(),
        ::Pair
    ).flatMapLatest { (chain, metaAccount) ->
        val xorAsset = chain.assets.firstOrNull { it.isUtility } ?: error("XOR asset not found")
        walletRepository.assetFlow(metaAccount.id, metaAccount.substrateAccountId, xorAsset, chain.minSupportedVersion)
    }

    private suspend fun getXorEuroPrice(priceId: String?): BigDecimal? {
        return soraCardRepository.getXorEuroPrice() ?: getCoingeckoXorPerEurRatio(priceId)
    }

    private suspend fun getCoingeckoXorPerEurRatio(priceId: String?): BigDecimal? = priceId?.let {
        walletRepository.getSingleAssetPriceCoingecko(priceId, "eur")
    }

    override fun subscribeToSoraCardAvailabilityFlow(): Flow<SoraCardAvailabilityInfo> = xorAssetFlow()
        .distinctUntilChanged(::compareByTotal)
        .map { asset ->
            val xorEuroPrice = getXorEuroPrice(asset.token.configuration.priceId) ?: return@map errorInfoState(BigDecimal.ZERO)

            val demeterStakedFarmed = BigDecimal.ZERO
            val poolBalance = BigDecimal.ZERO

            val totalXorBalance = asset.total.orZero().plus(poolBalance).plus(demeterStakedFarmed)

            try {
                val assetScale = asset.token.configuration.precision
                val xorRequiredBalanceWithBacklash = KYC_REQUIRED_BALANCE_WITH_BACKLASH.divide(xorEuroPrice, assetScale, RoundingMode.HALF_EVEN)
                val xorRealRequiredBalance = KYC_REAL_REQUIRED_BALANCE.divide(xorEuroPrice, assetScale, RoundingMode.HALF_EVEN)
                val xorBalanceInEur = totalXorBalance.multiply(xorEuroPrice)

                val needInXor = if (totalXorBalance.greaterThen(xorRealRequiredBalance)) {
                    BigDecimal.ZERO
                } else {
                    xorRequiredBalanceWithBacklash.minus(totalXorBalance)
                }

                val needInEur = if (xorBalanceInEur.greaterThen(KYC_REAL_REQUIRED_BALANCE)) {
                    BigDecimal.ZERO
                } else {
                    KYC_REQUIRED_BALANCE_WITH_BACKLASH.minus(xorBalanceInEur)
                }

                SoraCardAvailabilityInfo(
                    xorBalance = totalXorBalance,
                    enoughXor = totalXorBalance.greaterThen(xorRealRequiredBalance),
                    percent = totalXorBalance.divide(xorRealRequiredBalance, assetScale, RoundingMode.HALF_EVEN),
                    needInXor = needInXor.formatCrypto(),
                    needInEur = needInEur.formatFiat(),
                    xorRatioAvailable = true
                )
            } catch (t: Throwable) {
                errorInfoState(totalXorBalance)
            }
        }

    private fun errorInfoState(balance: BigDecimal) = SoraCardAvailabilityInfo(
        xorBalance = balance,
        enoughXor = false,
        xorRatioAvailable = false,
    )

    override suspend fun checkSoraCardPending() {
        var isLoopInProgress = true
        while (isLoopInProgress) {
            val status = soraCardClientProxy.getKycStatus().getOrDefault(SoraCardCommonVerification.NotFound)
            _soraCardStatus.value = status
            if (status != SoraCardCommonVerification.Pending) {
                isLoopInProgress = false
            } else {
                delay(POLLING_PERIOD_IN_MILLIS)
            }
        }
    }

    override fun subscribeSoraCardStatus(): Flow<SoraCardCommonVerification> =
        _soraCardStatus.asStateFlow()


    override fun setStatus(status: SoraCardCommonVerification) {
        _soraCardStatus.value = status
    }

    override fun setLogout() {
        _soraCardStatus.value = SoraCardCommonVerification.NotFound
    }

    private companion object {
        val KYC_REAL_REQUIRED_BALANCE: BigDecimal = BigDecimal.valueOf(95)
        val KYC_REQUIRED_BALANCE_WITH_BACKLASH: BigDecimal = BigDecimal.valueOf(100)
        const val POLLING_PERIOD_IN_MILLIS = 30_000L
    }
}
