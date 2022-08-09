package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorDetailsParcelToValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorDetailsToErrors
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.NominatorParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
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

class ValidatorDetailsViewModel @AssistedInject constructor(
    private val interactor: StakingInteractor,
    private val stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val router: StakingRouter,
    @Assisted private val validator: ValidatorDetailsParcelModel,
    private val iconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
) : BaseViewModel(), ExternalAccountActions.Presentation by externalAccountActions {

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    private val maxNominators = flowOf { stakingRelayChainScenarioInteractor.maxRewardedNominators() }
        .inBackground()

    val validatorDetails = maxNominators.combine(assetFlow) { maxNominators, asset ->
        val chain = interactor.getSelectedChain()

        mapValidatorDetailsParcelToValidatorDetailsModel(chain, validator, asset, maxNominators, iconGenerator, resourceManager)
    }
        .inBackground()
        .asLiveData()

    val errorFlow = flowOf { mapValidatorDetailsToErrors(validator) }
        .inBackground()
        .share()

    private val _openEmailEvent = MutableLiveData<Event<String>>()
    val openEmailEvent: LiveData<Event<String>> = _openEmailEvent

    private val _totalStakeEvent = MutableLiveData<Event<ValidatorStakeBottomSheet.Payload>>()
    val totalStakeEvent: LiveData<Event<ValidatorStakeBottomSheet.Payload>> = _totalStakeEvent

    fun backClicked() {
        router.back()
    }

    fun totalStakeClicked() {
        val validatorStake = validator.stake
        viewModelScope.launch {
            val asset = assetFlow.first()
            val payload = calculatePayload(asset, validatorStake)
            _totalStakeEvent.value = Event(payload)
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
        validator.identity?.web?.let {
            showBrowser(it)
        }
    }

    fun emailClicked() {
        validator.identity?.email?.let {
            _openEmailEvent.value = Event(it)
        }
    }

    fun twitterClicked() {
        validator.identity?.twitter?.let {
            showBrowser(appLinksProvider.getTwitterAccountUrl(it))
        }
    }

    fun accountActionsClicked() = launch {
        val address = validatorDetails.value?.address ?: return@launch
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

    @AssistedFactory
    interface ValidatorDetailsViewModelFactory {
        fun create(validator: ValidatorDetailsParcelModel): ValidatorDetailsViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: ValidatorDetailsViewModelFactory,
            validator: ValidatorDetailsParcelModel
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(validator) as T
            }
        }
    }
}
