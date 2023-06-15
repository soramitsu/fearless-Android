package jp.co.soramitsu.staking.impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.validators.details.CollatorDetailsFragment.Companion.KEY_COLLATOR
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.CollatorDetailsModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.IdentityModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CollatorDetailsViewModel @Inject constructor(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val iconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    rewardCalculatorFactory: RewardCalculatorFactory,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), ExternalAccountActions.Presentation by externalAccountActions {

    private val collator = savedStateHandle.get<CollatorDetailsParcelModel>(KEY_COLLATOR)!!

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val rewardCalculator = rewardCalculatorFactory.createSubquery()

    val collatorDetails = assetFlow.map { asset ->
        val totalStake = asset.token.amountFromPlanks(collator.stake.totalStake)
        val (statusText, statusColor) = mapStatus(collator.stake.status)
        val rewardApr = rewardCalculator.getApyFor(collator.accountIdHex.fromHex())

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
                    twitter = identity.twitter
                )
            },
            statusText = resourceManager.getString(statusText),
            statusColor = statusColor,
            delegations = collator.stake.delegations.toString(),
            estimatedRewardsApr = rewardApr.formatAsPercentage(),
            totalStake = totalStake.formatCryptoDetail(asset.token.configuration.symbol),
            totalStakeFiat = totalStake.let { asset.token.fiatAmount(it)?.formatFiat(asset.token.fiatSymbol) },
            minBond = collator.stake.minBond.formatCryptoDetailFromPlanks(asset.token.configuration),
            selfBonded = collator.stake.selfBonded.formatCryptoDetailFromPlanks(asset.token.configuration),
            effectiveAmountBonded = (collator.stake.totalStake - collator.stake.selfBonded).formatCryptoDetailFromPlanks(asset.token.configuration)
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
        val ownStakeFormatted = ownStake.formatCryptoDetail(asset.token.configuration.symbol)
        val ownStakeFiatFormatted = asset.token.fiatAmount(ownStake)?.formatFiat(asset.token.fiatSymbol)

        val nominatorsStakeValue = validatorStake.totalStake - validatorStake.selfBonded
        val nominatorsStake = asset.token.amountFromPlanks(nominatorsStakeValue)
        val nominatorsStakeFormatted = nominatorsStake.formatCryptoDetail(asset.token.configuration.symbol)
        val nominatorsStakeFiatFormatted = asset.token.fiatAmount(nominatorsStake)?.formatFiat(asset.token.fiatSymbol)

        val totalStake = asset.token.amountFromPlanks(validatorStake.totalStake)
        val totalStakeFormatted = totalStake.formatCryptoDetail(asset.token.configuration.symbol)
        val totalStakeFiatFormatted = asset.token.fiatAmount(totalStake)?.formatFiat(asset.token.fiatSymbol)

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
