package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeViewState
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.map

class AstarContributeViewState(
    private val interactor: AstarContributeInteractor,
    customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager
) : ReferralContributeViewState(
    customContributePayload = customContributePayload,
    resourceManager = resourceManager,
    fearlessReferralCode = interactor.fearlessReferralCode,
) {

    override val applyActionState = enteredReferralCodeFlow.map { referral ->
        when {
            referral.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_enter_referral_address))
            else -> ApplyActionState.Available
        }
    }

    private val bonusPayloadFlow = enteredReferralCodeFlow.map {
        createBonusPayload(it)
    }

    val bonusFriendFlow = bonusPayloadFlow.map {
        require(it is AstarBonusPayload)

        val tokenName = customContributePayload.parachainMetadata.token
        it.calculateFriendBonus(customContributePayload.amount)?.formatTokenAmount(tokenName)
    }

    override fun createBonusPayload(referralCode: String): ReferralCodePayload {
        return AstarBonusPayload(referralCode, customContributePayload.parachainMetadata.rewardRate)
    }

    override suspend fun validatePayload(payload: ReferralCodePayload) {
        val isReferralValid = interactor.isReferralValid(payload.referralCode)

        if (!isReferralValid) throw IllegalArgumentException(resourceManager.getString(R.string.crowdloan_astar_referral_code_invalid))
    }
}
