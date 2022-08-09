package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.account.api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_API_KEY
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_BONUS_RATE
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_TERMS_URL
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.CONTRIBUTE
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.getAsBigDecimal
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.util.regex.Pattern

enum class MoonbeamCrowdloanStep(val step: Int) {
    TERMS(0),
    TERMS_CONFIRM(1),
    TERMS_CONFIRM_SUCCESS(2),
    CONTRIBUTE(3),
    CONTRIBUTE_CONFIRM(4);

    fun next() = run {
        values().find { it.step == this.step + 1 }
    } ?: TERMS

    fun previous() = run {
        values().find { it.step == this.step - 1 }
    } ?: TERMS
}

class MoonbeamContributeViewState(
    private val interactor: MoonbeamContributeInteractor,
    val customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
    coroutineScope: CoroutineScope,
    accountUseCase: SelectedAccountUseCase
) : CustomContributeViewState {

    val title = customContributePayload.parachainMetadata.run {
        "$name ($token)"
    }

    val privacyAcceptedFlow = MutableStateFlow(customContributePayload.isPrivacyAccepted ?: false)

    suspend fun getSystemRemarkFee(): BigInteger {
        return interactor.getSystemRemarkFee(
            apiUrl = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_URL).orEmpty(),
            apiKey = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_KEY).orEmpty(),
            chainId = customContributePayload.chainId
        )
    }

    suspend fun doSystemRemark(): Boolean {
        return interactor.doSystemRemark(
            apiUrl = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_URL).orEmpty(),
            apiKey = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_KEY).orEmpty(),
            chainId = customContributePayload.chainId
        )
    }

    fun getRemarkTxHash(): String = interactor.getRemarkTxHash()

    suspend fun termsText(): String =
        customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_TERMS_URL)?.let {
            interactor.getTerms(url = it, chainId = customContributePayload.chainId)
        }.orEmpty()

    val enteredAmountFlow = MutableStateFlow("")

    private val selectedAddressModelFlow = accountUseCase.selectedAccountFlow()
        .asLiveData(coroutineScope)

    private val savedEthAddress: String? = selectedAddressModelFlow.value?.address?.let { address ->
        interactor.getEthAddress(
            paraId = customContributePayload.paraId,
            address = address
        )
    }

    val enteredEtheriumAddressFlow = MutableStateFlow(savedEthAddress.orEmpty())

    fun isEtheriumAddressCorrectAndOld(): Pair<Boolean, Boolean> {
        val address = enteredEtheriumAddressFlow.value
        val pattern = "0x[A-Fa-f0-9]{40}"
        return Pattern.matches(pattern, address) to (address == savedEthAddress)
    }

    suspend fun getContributionSignature(amount: BigInteger): String = interactor.getContributionSignature(
        apiUrl = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_URL).orEmpty(),
        apiKey = customContributePayload.parachainMetadata.flow?.data?.getString(FLOW_API_KEY).orEmpty(),
        contribution = amount,
        paraId = customContributePayload.paraId,
        chainId = customContributePayload.chainId
    )

    override val applyActionState = when (customContributePayload.step) {
        TERMS -> privacyAcceptedFlow.map { privacyAccepted ->
            when {
                privacyAccepted -> ApplyActionState.Available
                else -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
            }
        }
        CONTRIBUTE -> enteredEtheriumAddressFlow.combine(enteredAmountFlow) { ethAddress, amount ->
            when {
                amount.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
                ethAddress.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
                else -> ApplyActionState.Available
            }
        }
        else -> flow {
            ApplyActionState.Available
        }
    }

    override suspend fun generatePayload(): Result<BonusPayload> = runCatching {
        val payload = createBonusPayload()
        payload
    }

    private fun createBonusPayload(): ReferralCodePayload {
        return MoonbeamBonusPayload(
            referralCode = enteredEtheriumAddressFlow.value,
            parachainId = customContributePayload.paraId,
            rewardRate = customContributePayload.parachainMetadata.rewardRate,
            bonusRate = customContributePayload.parachainMetadata.flow?.data?.getAsBigDecimal(FLOW_BONUS_RATE)
        )
    }
}
