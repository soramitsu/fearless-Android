package jp.co.soramitsu.crowdloan.impl.presentation.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.list.toValueList
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.mapLoading
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.resources.formatTimeLeft
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.impl.data.CrowdloanSharedState
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_API_KEY
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.crowdloan.impl.domain.main.Crowdloan
import jp.co.soramitsu.crowdloan.impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.CrowdloanRouter
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.CONTRIBUTE
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.getString
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.mapParachainMetadataToParcel
import jp.co.soramitsu.crowdloan.impl.presentation.main.model.CrowdloanModel
import jp.co.soramitsu.crowdloan.impl.presentation.main.model.CrowdloanStatusModel
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.chain
import jp.co.soramitsu.runtime.state.selectedChainFlow
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.wallet.api.domain.AssetUseCase
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoFromPlanks
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.WithAssetSelector
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val ICON_SIZE_DP = 40

@HiltViewModel
class CrowdloanViewModel @Inject constructor(
    private val interactor: CrowdloanInteractor,
    @Named("CrowdloanAssetUseCase") private val assetUseCase: AssetUseCase,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val crowdloanSharedState: CrowdloanSharedState,
    private val router: CrowdloanRouter,
    private val sharedState: CrowdloanSharedState,
    @Named("CrowdloanChainUpdateSystem") private val crowdloanUpdateSystem: UpdateSystem,
    @Named("CrowdloanAssetSelector") assetSelectorFactory: AssetSelectorMixin.Presentation.Factory,
    private val accountInteractor: AccountInteractor,
    private val clipboardManager: ClipboardManager
) : BaseViewModel(), WithAssetSelector, Browserable {

    override val assetSelectorMixin = assetSelectorFactory.create(scope = this)

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val _learnMoreLiveData = MutableLiveData<String>()
    val learnMoreLiveData: LiveData<String> = _learnMoreLiveData
    val blockingProgress = MutableStateFlow(false)

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val mainDescription = assetFlow.map {
        resourceManager.getString(R.string.crowdloan_main_description, it.token.configuration.symbol.uppercase())
    }

    private val selectedChain = sharedState.selectedChainFlow()
        .share()

    private val refreshFlow = MutableStateFlow(Event(Unit))

    private val groupedCrowdloansFlow = refreshFlow.flatMapLatest {
        selectedChain.withLoading {
            interactor.crowdloansFlow(it)
        }
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
        _learnMoreLiveData.value = BuildConfig.WIKI_CROWDLOANS_URL.removePrefix("https://")
    }

    private fun mapCrowdloanStatusToUi(statusClass: KClass<out Crowdloan.State>, statusCount: Int): CrowdloanStatusModel? {
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

        val raisedDisplay = crowdloan.fundInfo.raised.formatCryptoDetailFromPlanks(token.configuration, false)
        val capDisplay = crowdloan.fundInfo.cap.formatCryptoDetailFromPlanks(token.configuration)

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
            val myContributionFormatted = it.amount.formatCryptoFromPlanks(token.configuration)

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
            referral = when (crowdloan.parachainMetadata?.isInterlay) {
                true -> accountInteractor.selectedMetaAccountFlow().firstOrNull()?.substrateAccountId?.toAddress(0.toShort())?.toByteArray()?.blake2b256()?.toHexString(true)
                else -> null
            }
        )
    }

    private suspend fun generateDepositorIcon(depositorAddress: String): CrowdloanModel.Icon {
        val icon = iconGenerator.createAddressIcon(depositorAddress, ICON_SIZE_DP)

        return CrowdloanModel.Icon.FromDrawable(icon)
    }

    fun crowdloanClicked(chainId: ChainId, paraId: ParaId) {
        launch {
            val crowdloans = crowdloansFlow.first() as? LoadingState.Loaded ?: return@launch
            val crowdloan = crowdloans.data.firstOrNull { it.parachainId == paraId } ?: return@launch
            blockingProgress.value = true
            val payload = ContributePayload(
                chainId = chainId,
                paraId = crowdloan.parachainId,
                parachainMetadata = crowdloan.parachainMetadata?.let(::mapParachainMetadataToParcel)
            )

            if (crowdloan.parachainMetadata?.isMoonbeam == true) {
                val apiUrl = crowdloan.parachainMetadata.flow?.data?.getString(FLOW_API_URL)
                val apiKey = crowdloan.parachainMetadata.flow?.data?.getString(FLOW_API_KEY)
                val signedResult = when {
                    apiUrl == null || apiKey == null -> Result.success(false)
                    else -> interactor.checkRemark(apiUrl, apiKey)
                }

                if (signedResult.isFailure) {
                    showError(signedResult.exceptionOrNull()?.message.orEmpty())
                    blockingProgress.value = false
                    return@launch
                }

                val isSigned = signedResult.getOrDefault(false)
                val startStep = when {
                    isSigned -> CONTRIBUTE
                    else -> TERMS
                }

                val customContributePayload = CustomContributePayload(
                    chainId = chainId,
                    paraId = payload.paraId,
                    parachainMetadata = payload.parachainMetadata!!,
                    step = startStep,
                    amount = BigDecimal.ZERO,
                    previousBonusPayload = router.latestCustomBonus
                )

                router.openMoonbeamContribute(customContributePayload)
            } else {
                router.openContribute(payload)
            }
            blockingProgress.value = false
        }
    }

    fun learnMoreClicked() {
        openBrowserEvent.value = Event(BuildConfig.WIKI_CROWDLOANS_URL)
    }

    fun copyStringClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    fun refresh() {
        launch {
            refreshFlow.emit(Event(Unit))
        }
    }

    fun backClicked() {
        router.back()
    }
}
