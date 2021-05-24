package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import android.text.format.DateUtils
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.remainingTimeInSeconds
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataToParcel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model.CrowdloanModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val ICON_SIZE_DP = 40

class CrowdloanViewModel(
    private val interactor: CrowdloanInteractor,
    private val assetUseCase: AssetUseCase,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val router: CrowdloanRouter
) : BaseViewModel() {

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val mainDescription = assetFlow.map {
        resourceManager.getString(R.string.crowdloan_main_description, it.token.type.displayName)
    }

    private val crowdloansFlow = interactor.crowdloansFlow()
        .inBackground()
        .share()

    val crowdloanModelsFlow = crowdloansFlow.combine(assetFlow) { crowdloans, asset ->
        crowdloans.map { mapCrowdloanToCrowdloanModel(it, asset) }
    }
        .withLoading()
        .inBackground()
        .share()

    private suspend fun mapCrowdloanToCrowdloanModel(crowdloan: Crowdloan, asset: Asset): CrowdloanModel {
        val token = asset.token

        val raisedDisplay = token.amountFromPlanks(crowdloan.raised).format()
        val capDisplay = token.amountFromPlanks(crowdloan.cap).formatTokenAmount(token.type)

        val depositorAddress = crowdloan.depositor.toAddress(token.type.networkType)

        val icon = if (crowdloan.parachainMetadata != null) {
            CrowdloanModel.Icon.FromLink(crowdloan.parachainMetadata.iconLink)
        } else {
            generateDepositorIcon(depositorAddress)
        }

        val stateFormatted = when (val state = crowdloan.state) {
            Crowdloan.State.Finished -> CrowdloanModel.State.Finished

            is Crowdloan.State.Active -> {
                val timeLeft = DateUtils.formatElapsedTime(state.remainingTimeInSeconds)

                CrowdloanModel.State.Active(
                    timeRemaining = resourceManager.getString(R.string.common_time_left_format, timeLeft)
                )
            }
        }

        return CrowdloanModel(
            parachainId = crowdloan.parachainId,
            title = crowdloan.parachainMetadata?.name ?: depositorAddress,
            description = crowdloan.parachainMetadata?.description,
            icon = icon,
            raised = resourceManager.getString(R.string.crownloans_raised_format, raisedDisplay, capDisplay),
            state = stateFormatted,
        )
    }

    private suspend fun generateDepositorIcon(depositorAddress: String): CrowdloanModel.Icon {
        val icon = iconGenerator.createAddressIcon(depositorAddress, ICON_SIZE_DP)

        return CrowdloanModel.Icon.FromDrawable(icon)
    }

    fun crowdloanClicked(index: Int) {
        launch {
            val crowdloan = crowdloansFlow.first().getOrNull(index) ?: return@launch

            val payload = ContributePayload(
                paraId = crowdloan.parachainId,
                parachainMetadata = crowdloan.parachainMetadata?.let(::mapParachainMetadataToParcel)
            )

            router.openContribute(payload)
        }
    }
}
