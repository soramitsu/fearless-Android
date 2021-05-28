package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
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

class KaruraContributeViewState(
    private val interactor: KaruraContributeInteractor,
    private val customContributePayload: CustomContributePayload,
    private val resourceManager: ResourceManager
) : CustomContributeViewState {

    private val fearlessReferralCode = interactor.fearlessReferralCode()

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
        text = resourceManager.getString(R.string.crowdloan_learn_bonuses, customContributePayload.parachainMetadata.name)
    )

    private val bonusPayload = enteredReferralCodeFlow.map {
        KaruraBonusPayload(enteredReferralCodeFlow.value, customContributePayload.parachainMetadata.rewardRate)
    }

    val bonusLiveData = bonusPayload.map {
        val tokenName = customContributePayload.parachainMetadata.token

        it.calculateBonus(customContributePayload.amount).formatTokenAmount(tokenName)
    }

    init {
        previouslySetBonus()?.let {
            enteredReferralCodeFlow.value = it.referralCode
            privacyAcceptedFlow.value = true
        }
    }

    fun applyFearlessCode() {
        enteredReferralCodeFlow.value = fearlessReferralCode
    }

    fun privacyClicked() {
        openWebsite()
    }

    fun learnMoreClicked() {
        openWebsite()
    }

    override suspend fun generatePayload(): Result<BonusPayload> = runCatching {
        val payload = bonusPayload.first()

        val isReferralValid = interactor.isReferralValid(payload.referralCode)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.referral_code_is_invalid))

        payload
    }

    override val applyActionState = enteredReferralCodeFlow.combine(privacyAcceptedFlow) { referral, privacyAccepted ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral))
            privacyAccepted.not() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_agree_with_policy))
            else -> ApplyActionState.Available
        }
    }

    private fun openWebsite() {
        _openBrowserFlow.tryEmit(customContributePayload.parachainMetadata.website)
    }

    private fun createFearlessBonusTitle(): String {
        val percentage = KARURA_BONUS_MULTIPLIER.fractionToPercentage().formatAsPercentage()

        return resourceManager.getString(R.string.crowdloan_fearless_bonus, percentage)
    }

    private fun previouslySetBonus() = customContributePayload.previousBonusPayload as? KaruraBonusPayload
}
