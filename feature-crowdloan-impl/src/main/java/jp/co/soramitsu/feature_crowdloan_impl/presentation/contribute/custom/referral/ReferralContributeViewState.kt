package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_FEARLESS_REFERRAL
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_TERMS_URL
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnMoreModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getString
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

abstract class ReferralContributeViewState(
    protected val customContributePayload: CustomContributePayload,
    protected val resourceManager: ResourceManager,
    private val learnMoreUrl: String = customContributePayload.parachainMetadata.website,
) : CustomContributeViewState {

    private val fearlessReferral = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_FEARLESS_REFERRAL)
    private val termsUrl: String =
        customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_TERMS_URL) ?: customContributePayload.parachainMetadata.website

    abstract fun createBonusPayload(
        referralCode: String,
        email: String? = null,
        agreeReceiveEmail: Boolean? = null
    ): ReferralCodePayload

    abstract suspend fun validatePayload(payload: ReferralCodePayload)

    private val _openBrowserFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val openBrowserFlow: Flow<String> = _openBrowserFlow
    val isAstar = customContributePayload.parachainMetadata.isAstar
    val isAcala = customContributePayload.parachainMetadata.isAcala

    val enteredReferralCodeFlow = MutableStateFlow("")
    val enteredEmailFlow = MutableStateFlow("")

    val privacyAcceptedFlow = MutableStateFlow(false)
    val emailAgreedFlow = MutableStateFlow(false)

    val applyFearlessCodeEnabledFlow = enteredReferralCodeFlow.map {
        it != fearlessReferral
    }

    val learnBonusesTitle = LearnMoreModel(
        iconLink = customContributePayload.parachainMetadata.iconLink,
        text = resourceManager.getString(R.string.crowdloan_learn_bonuses, customContributePayload.parachainMetadata.name)
    )

    init {
        previousPayload()?.let {
            enteredReferralCodeFlow.value = it.referralCode
            privacyAcceptedFlow.value = true
        }
    }

    fun applyFearlessCode() {
        fearlessReferral?.let { enteredReferralCodeFlow.value = it }
    }

    fun termsClicked() {
        _openBrowserFlow.tryEmit(termsUrl)
    }

    fun learnMoreClicked() {
        _openBrowserFlow.tryEmit(learnMoreUrl)
    }

    val emailValidationFlow = when {
        isAcala -> enteredEmailFlow.combine(emailAgreedFlow) { input, agreed ->
            when {
                !agreed -> true
                input.length > 2 && agreed -> true
                else -> false
            }
        }
        else -> flow {
            emit(true)
        }
    }

    private val bonusPayloadFlow = enteredReferralCodeFlow.combine(enteredEmailFlow) { referral, email ->
        val agreeReceiveEmail = emailAgreedFlow.value
        createBonusPayload(referral, email, agreeReceiveEmail)
    }

    val bonusNumberFlow = bonusPayloadFlow.map {
        it.calculateBonus(customContributePayload.amount)
    }

    val bonusFlow = bonusNumberFlow.map { bonus ->
        val tokenName = customContributePayload.parachainMetadata.token
        bonus?.formatTokenAmount(tokenName)
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

    protected fun previousPayload() = customContributePayload.previousBonusPayload as? ReferralCodePayload
}
