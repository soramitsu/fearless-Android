package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
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
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.PERCENT_MULTIPLIER
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.CollatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.IdentityModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.NominatorParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioInteractor
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
import java.math.BigDecimal

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

        // mapValidatorDetailsParcelToValidatorDetailsModel(chain, collator, asset, maxDelegations, iconGenerator, resourceManager)
        val totalStake = asset.token.amountFromPlanks(collator.stake.totalStake)
        CollatorDetailsModel(
            "0x${collator.accountIdHex}",
            iconGenerator.createAddressModel(collator.accountIdHex, 24).image,
            IdentityModel(
                display = collator.identity?.display,
                legal = collator.identity?.legal,
                web = collator.identity?.web,
                riot = collator.identity?.riot,
                email = collator.identity?.email,
                pgpFingerprint = collator.identity?.pgpFingerprint,
                image = collator.identity?.image,
                twitter = collator.identity?.twitter,
            ),
            statusText = if (collator.stake.elected) resourceManager.getString(R.string.staking_your_elected) else collator.request,
            statusColor = if (collator.stake.elected) R.color.green else R.color.red,
            delegations = collator.stake.delegations.format(),
            estimatedRewardsApr = (PERCENT_MULTIPLIER * BigDecimal.ONE).formatAsPercentage(),
            totalStake = totalStake.formatTokenAmount(asset.token.configuration),
            totalStakeFiat = asset.token.fiatAmount(totalStake)?.formatAsCurrency(asset.token.fiatSymbol),
            minBond = asset.token.amountFromPlanks(stakingParachainScenarioInteractor.getMinimumStake(asset.token.configuration.chainId)).formatTokenAmount(asset.token.configuration),
            selfBonded = asset.token.amountFromPlanks(collator.stake.selfBonded).formatTokenAmount(asset.token.configuration),
            effectiveAmountBonded = "effective",
        )
    }
        .inBackground()
        .asLiveData()

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
        val validatorStake = collator.stake
        viewModelScope.launch {
            val asset = assetFlow.first()
            // val payload = calculatePayload(asset, validatorStake)
            // _totalStakeEvent.value = Event(payload)
        }
    }

    private suspend fun calculatePayload(asset: Asset, validatorStake: ValidatorStakeParcelModel) = withContext(Dispatchers.Default) {
        require(validatorStake is ValidatorStakeParcelModel.Active)

        val ownStake = asset.token.amountFromPlanks(validatorStake.ownStake)
        val ownStakeFormatted = ownStake.formatTokenAmount(asset.token.configuration)
        val ownStakeFiatFormatted = asset.token.fiatAmount(ownStake)?.formatAsCurrency(asset.token.fiatSymbol)

        val nominatorsStakeValue = validatorStake.nominators.sumByBigInteger(NominatorParcelModel::value)
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
            resourceManager.getString(R.string.staking_validator_nominators),
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
