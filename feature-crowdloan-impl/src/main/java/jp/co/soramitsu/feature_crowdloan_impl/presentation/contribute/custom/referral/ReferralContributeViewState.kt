package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnMoreModel
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

abstract class ReferralContributeViewState(
    protected val customContributePayload: CustomContributePayload,
    protected val resourceManager: ResourceManager,
    private val fearlessReferralCode: String,
    private val bonusPercentage: BigDecimal,
    private val termsUrl: String = customContributePayload.parachainMetadata.website,
    private val learnMoreUrl: String = customContributePayload.parachainMetadata.website,
) : CustomContributeViewState {

    abstract fun createBonusPayload(referralCode: String): ReferralCodePayload

    abstract suspend fun validatePayload(payload: ReferralCodePayload)

    private val _openBrowserFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val openBrowserFlow: Flow<String> = _openBrowserFlow

    val enteredReferralCodeFlow = MutableStateFlow("")

    val privacyAcceptedFlow = MutableStateFlow(false)

    val applyFearlessTitle = createFearlessBonusTitle()

    val applyFearlessCodeEnabledFlow = enteredReferralCodeFlow.map {
        it != fearlessReferralCode
    }

    val learnBonusesTitle = LearnMoreModel(
        iconLink = customContributePayload.parachainMetadata.iconLink,
        text = resourceManager.getString(R.string.crowdloan_learn, customContributePayload.parachainMetadata.name)
    )

    private val bonusPayloadFlow = enteredReferralCodeFlow.map {
        createBonusPayload(it)
    }

    val bonusFlow = bonusPayloadFlow.map {
        val tokenName = customContributePayload.parachainMetadata.token

        it.calculateBonus(customContributePayload.amount)?.formatTokenAmount(tokenName)
    }

    init {
        previousPayload()?.let {
            enteredReferralCodeFlow.value = it.referralCode
            privacyAcceptedFlow.value = true
        }
    }

    fun applyFearlessCode() {
        enteredReferralCodeFlow.value = fearlessReferralCode
    }

    fun termsClicked() {
        _openBrowserFlow.tryEmit(termsUrl)
    }

    fun learnMoreClicked() {
        _openBrowserFlow.tryEmit(learnMoreUrl)
    }

    override val applyActionState = enteredReferralCodeFlow.combine(privacyAcceptedFlow) { referral, privacyAccepted ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral))
            privacyAccepted.not() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_empty_bonus_title))
            else -> ApplyActionState.Available
        }
    }

    override suspend fun generatePayload(): Result<BonusPayload> = runCatching {
        val payload = bonusPayloadFlow.first()

        validatePayload(payload)

        payload
    }

    private fun createFearlessBonusTitle(): String {
        val percentage = bonusPercentage.fractionToPercentage().formatAsPercentage()

        return resourceManager.getString(R.string.crowdloan_app_bonus_format, percentage)
    }

    protected fun previousPayload() = customContributePayload.previousBonusPayload as? ReferralCodePayload
}
