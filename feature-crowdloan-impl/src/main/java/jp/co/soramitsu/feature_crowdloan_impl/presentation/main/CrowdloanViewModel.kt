package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.list.toValueList
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.mapLoading
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.resources.formatTimeLeft
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataToParcel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model.CrowdloanModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model.CrowdloanStatusModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.WithAssetSelector
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.chain
import jp.co.soramitsu.runtime.state.selectedChainFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private const val ICON_SIZE_DP = 40

class CrowdloanViewModel(
    private val interactor: CrowdloanInteractor,
    private val assetUseCase: AssetUseCase,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val crowdloanSharedState: CrowdloanSharedState,
    private val router: CrowdloanRouter,
    private val sharedState: CrowdloanSharedState,
    private val crowdloanUpdateSystem: UpdateSystem,
    assetSelectorFactory: AssetSelectorMixin.Presentation.Factory,
) : BaseViewModel(), WithAssetSelector {

    override val assetSelectorMixin = assetSelectorFactory.create(scope = this)

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val mainDescription = assetFlow.map {
        resourceManager.getString(R.string.crowdloan_main_description, it.token.configuration.symbol)
    }

    private val selectedChain = sharedState.selectedChainFlow()
        .share()

    private val groupedCrowdloansFlow = selectedChain.withLoading {
        interactor.crowdloansFlow(it)
    }
        .inBackground()
        .share()

    private val crowdloansFlow = groupedCrowdloansFlow
        .mapLoading { it.toValueList() }
        .inBackground()
        .share()

    val crowdloanModelsFlow = groupedCrowdloansFlow.mapLoading { groupedCrowdloans ->
        val asset = assetFlow.first()
        val chain = crowdloanSharedState.chain()

        groupedCrowdloans
            .mapKeys { (statusClass, values) -> mapCrowdloanStatusToUi(statusClass, values.size) }
            .mapValues { (_, crowdloans) -> crowdloans.map { mapCrowdloanToCrowdloanModel(chain, it, asset) } }
            .toListWithHeaders()
    }
        .inBackground()
        .share()

    init {
        crowdloanUpdateSystem.start()
            .launchIn(this)
    }

    private fun mapCrowdloanStatusToUi(statusClass: KClass<out Crowdloan.State>, statusCount: Int): CrowdloanStatusModel {
        return when (statusClass) {
            Crowdloan.State.Finished::class -> CrowdloanStatusModel(
                text = resourceManager.getString(R.string.common_completed_with_count, statusCount),
                textColorRes = R.color.black1
            )
            Crowdloan.State.Active::class -> CrowdloanStatusModel(
                text = resourceManager.getString(R.string.crowdloan_active_section_format, statusCount),
                textColorRes = R.color.green
            )
            else -> throw IllegalArgumentException("Unsupported crowdloan status type: ${statusClass.simpleName}")
        }
    }

    private suspend fun mapCrowdloanToCrowdloanModel(
        chain: Chain,
        crowdloan: Crowdloan,
        asset: Asset
    ): CrowdloanModel {
        val token = asset.token

        val raisedDisplay = token.amountFromPlanks(crowdloan.fundInfo.raised).format()
        val capDisplay = token.amountFromPlanks(crowdloan.fundInfo.cap).formatTokenAmount(token.configuration)

        val depositorAddress = chain.addressOf(crowdloan.fundInfo.depositor)

        val icon = if (crowdloan.parachainMetadata != null) {
            CrowdloanModel.Icon.FromLink(crowdloan.parachainMetadata.iconLink)
        } else {
            generateDepositorIcon(depositorAddress)
        }

        val stateFormatted = when (val state = crowdloan.state) {
            Crowdloan.State.Finished -> CrowdloanModel.State.Finished

            is Crowdloan.State.Active -> {
                CrowdloanModel.State.Active(
                    timeRemaining = resourceManager.formatTimeLeft(state.remainingTimeInMillis)
                )
            }
        }

        val myContributionDisplay = crowdloan.myContribution?.let {
            val myContributionFormatted = token.amountFromPlanks(it.amount).formatTokenAmount(token.configuration)

            resourceManager.getString(R.string.crowdloan_contribution_format, myContributionFormatted)
        }

        return CrowdloanModel(
            relaychainId = chain.id,
            parachainId = crowdloan.parachainId,
            title = crowdloan.parachainMetadata?.name ?: crowdloan.parachainId.toString(),
            description = crowdloan.parachainMetadata?.description ?: depositorAddress,
            icon = icon,
            raised = resourceManager.getString(R.string.crownloans_raised_format, raisedDisplay, capDisplay),
            myContribution = myContributionDisplay,
            state = stateFormatted,
        )
    }

    private suspend fun generateDepositorIcon(depositorAddress: String): CrowdloanModel.Icon {
        val icon = iconGenerator.createAddressIcon(depositorAddress, ICON_SIZE_DP)

        return CrowdloanModel.Icon.FromDrawable(icon)
    }

    fun crowdloanClicked(paraId: ParaId) {
        launch {
            val crowdloans = crowdloansFlow.first() as? LoadingState.Loaded ?: return@launch
            val crowdloan = crowdloans.data.firstOrNull { it.parachainId == paraId } ?: return@launch

            val payload = ContributePayload(
                paraId = crowdloan.parachainId,
                parachainMetadata = crowdloan.parachainMetadata?.let(::mapParachainMetadataToParcel)
            )

            router.openContribute(payload)
        }
    }
}
