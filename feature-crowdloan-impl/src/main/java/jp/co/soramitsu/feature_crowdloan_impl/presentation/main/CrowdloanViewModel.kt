package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import android.text.format.DateUtils
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.remainingTimeInSeconds
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model.CrowdloanModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.combine

private const val ICON_SIZE_DP = 40

class CrowdloanViewModel(
    private val interactor: CrowdloanInteractor,
    private val assetUseCase: AssetUseCase,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    val crowdloanModelsFlow = interactor.crowdloansFlow().combine(assetUseCase.currentAssetFlow()) { crowdloans, asset ->
        crowdloans.map { mapCrowdloanToCrowdloanModel(it, asset) }
    }
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

        val timeLeft = DateUtils.formatElapsedTime(crowdloan.remainingTimeInSeconds)

        return CrowdloanModel(
            parachainId = crowdloan.parachainId,
            title = crowdloan.parachainMetadata?.name ?: depositorAddress,
            description = crowdloan.parachainMetadata?.description,
            icon = icon,
            raised = resourceManager.getString(R.string.crownloans_raised, raisedDisplay, capDisplay),
            timeRemaining = resourceManager.getString(R.string.common_time_left, timeLeft)
        )
    }

    private suspend fun generateDepositorIcon(depositorAddress: String): CrowdloanModel.Icon {
        val icon = iconGenerator.createAddressIcon(depositorAddress, ICON_SIZE_DP)

        return CrowdloanModel.Icon.FromDrawable(icon)
    }
}
