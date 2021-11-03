package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.util.regex.Pattern

class MoonbeamContributeViewState(
    private val interactor: MoonbeamContributeInteractor,
    val customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
    coroutineScope: CoroutineScope,
    accountUseCase: SelectedAccountUseCase,
) : CustomContributeViewState {

    val title = customContributePayload.parachainMetadata.run {
        "$name ($token)"
    }

    init {
        interactor.nextStep(customContributePayload)
    }

    val privacyAcceptedFlow = MutableStateFlow(customContributePayload.isPrivacyAccepted ?: false)

    suspend fun getSystemRemarkFee(): BigInteger {
        return interactor.getSystemRemarkFee(
            apiUrl = customContributePayload.parachainMetadata.flow?.data?.baseUrl.orEmpty(),
            apiKey = customContributePayload.parachainMetadata.flow?.data?.apiKey.orEmpty()
        )
    }

    suspend fun doSystemRemark(): Boolean {
        return interactor.doSystemRemark(
            apiUrl = customContributePayload.parachainMetadata.flow?.data?.baseUrl.orEmpty(),
            apiKey = customContributePayload.parachainMetadata.flow?.data?.apiKey.orEmpty()
        )
    }

    fun getRemarkTxHash(): String = interactor.getRemarkTxHash()

    suspend fun termsText(): String =
        customContributePayload.parachainMetadata.flow?.data?.termsUrl?.let {
            interactor.getTerms(it)
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

    suspend fun getContributionSignature(amount: BigInteger): String {
        return interactor.getContributionSignature(
            apiUrl = customContributePayload.parachainMetadata.flow?.data?.baseUrl.orEmpty(),
            apiKey = customContributePayload.parachainMetadata.flow?.data?.apiKey.orEmpty(),
            contribution = amount,
            paraId = customContributePayload.paraId,
        )
    }

    override val applyActionState = when (customContributePayload.step) {
        0 -> privacyAcceptedFlow.map { privacyAccepted ->
            when {
                privacyAccepted -> ApplyActionState.Available
                else -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
            }
        }
        3 -> enteredEtheriumAddressFlow.combine(enteredAmountFlow) { ethAddress, amount ->
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
        return MoonbeamBonusPayload(enteredEtheriumAddressFlow.value, customContributePayload.paraId, customContributePayload.parachainMetadata.rewardRate)
    }
}
