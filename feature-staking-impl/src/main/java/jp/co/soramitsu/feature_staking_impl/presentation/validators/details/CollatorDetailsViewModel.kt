package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.PERCENT_MULTIPLIER
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.CollatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.IdentityModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
    private val router: StakingRouter,
    private val collator: CollatorDetailsParcelModel,
    private val iconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
) : BaseViewModel(), ExternalAccountActions.Presentation by externalAccountActions {

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    private val maxDelegations = flowOf { stakingParachainScenarioInteractor.maxDelegationsPerDelegator() }
        .inBackground()

    val collatorDetails = maxDelegations.combine(assetFlow) { maxDelegations, asset ->
        val chain = interactor.getSelectedChain()
        val address = interactor.getSelectedAccountProjection()?.address
        val atStake = stakingParachainScenarioInteractor.getAtStake(chain.id, collator.accountIdHex.fromHex()).getOrNull()
        val myTotalStake = atStake?.delegations?.find { it.first.toHexString(true) == address }?.second
        val totalStake = myTotalStake?.let { asset.token.amountFromPlanks(it) }
        val (statusText, statusColor) = mapStatus(collator.stake.status)
        CollatorDetailsModel(
            "0x${collator.accountIdHex}",
            iconGenerator.createEthereumAddressModel(collator.accountIdHex, AddressIconGenerator.SIZE_MEDIUM).image,
            collator.identity?.let { identity ->
                IdentityModel(
                    display = identity.display,
                    legal = identity.legal,
                    web = identity.web,
                    riot = identity.riot,
                    email = identity.email,
                    pgpFingerprint = identity.pgpFingerprint,
                    image = identity.image,
                    twitter = identity.twitter,
                )
            },
            statusText = resourceManager.getString(statusText),
            statusColor = statusColor,
            delegations = collator.stake.delegations.format(),
            estimatedRewardsApr = (PERCENT_MULTIPLIER * BigDecimal.ONE).formatAsPercentage(),
            totalStake = totalStake?.formatTokenAmount(asset.token.configuration),
            totalStakeFiat = totalStake?.let { asset.token.fiatAmount(it)?.formatAsCurrency(asset.token.fiatSymbol) },
            minBond = asset.token.amountFromPlanks(collator.stake.minBond).formatTokenAmount(asset.token.configuration),
            selfBonded = asset.token.amountFromPlanks(collator.stake.selfBonded).formatTokenAmount(asset.token.configuration),
            effectiveAmountBonded = asset.token.amountFromPlanks(collator.stake.totalStake).formatTokenAmount(asset.token.configuration),
        )
    }
        .inBackground()
        .asLiveData()

    private fun mapStatus(status: CandidateInfoStatus) =
        when (status) {
            CandidateInfoStatus.ACTIVE -> R.string.staking_nominator_status_active to R.color.green
            CandidateInfoStatus.EMPTY,
            CandidateInfoStatus.IDLE -> R.string.staking_collator_status_idle to R.color.colorGreyText
            is CandidateInfoStatus.LEAVING -> R.string.staking_collator_status_leaving to R.color.colorGreyText
        }

//    val errorFlow = flowOf { mapValidatorDetailsToErrors(validator) }
//        .inBackground()
//        .share()

    private val _openEmailEvent = MutableLiveData<Event<String>>()
    val openEmailEvent: LiveData<Event<String>> = _openEmailEvent

    private val _totalStakeEvent = MutableLiveData<Event<ValidatorStakeBottomSheet.Payload>>()
    val totalStakeEvent: LiveData<Event<ValidatorStakeBottomSheet.Payload>> = _totalStakeEvent

    fun backClicked() {
        router.back()
    }

    fun totalStakeClicked() {
        val collatorStake = collator.stake
        viewModelScope.launch {
            val asset = assetFlow.first()
            val payload = calculatePayload(asset, collatorStake)
            _totalStakeEvent.value = Event(payload)
        }
    }

    private suspend fun calculatePayload(asset: Asset, validatorStake: CollatorStakeParcelModel) = withContext(Dispatchers.Default) {
        val ownStake = asset.token.amountFromPlanks(validatorStake.selfBonded)
        val ownStakeFormatted = ownStake.formatTokenAmount(asset.token.configuration)
        val ownStakeFiatFormatted = asset.token.fiatAmount(ownStake)?.formatAsCurrency(asset.token.fiatSymbol)

        val nominatorsStakeValue = validatorStake.totalStake - validatorStake.selfBonded
        val nominatorsStake = asset.token.amountFromPlanks(nominatorsStakeValue)
        val nominatorsStakeFormatted = nominatorsStake.formatTokenAmount(asset.token.configuration)
        val nominatorsStakeFiatFormatted = asset.token.fiatAmount(nominatorsStake)?.formatAsCurrency(asset.token.fiatSymbol)

        val totalStake = asset.token.amountFromPlanks(validatorStake.totalStake)
        val totalStakeFormatted = totalStake.formatTokenAmount(asset.token.configuration)
        val totalStakeFiatFormatted = asset.token.fiatAmount(totalStake)?.formatAsCurrency(asset.token.fiatSymbol)

        ValidatorStakeBottomSheet.Payload(
            resourceManager.getString(R.string.staking_validator_own_stake),
            ownStakeFormatted,
            ownStakeFiatFormatted,
            resourceManager.getString(R.string.collator_details_delegators),
            nominatorsStakeFormatted,
            nominatorsStakeFiatFormatted,
            resourceManager.getString(R.string.wallet_send_total_title),
            totalStakeFormatted,
            totalStakeFiatFormatted
        )
    }

    fun webClicked() {
        collator.identity?.web?.let {
            showBrowser(it)
        }
    }

    fun emailClicked() {
        collator.identity?.email?.let {
            _openEmailEvent.value = Event(it)
        }
    }

    fun twitterClicked() {
        collator.identity?.twitter?.let {
            showBrowser(appLinksProvider.getTwitterAccountUrl(it))
        }
    }

    fun accountActionsClicked() = launch {
        val address = collatorDetails.value?.address ?: return@launch
        val chainId = assetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }
}
