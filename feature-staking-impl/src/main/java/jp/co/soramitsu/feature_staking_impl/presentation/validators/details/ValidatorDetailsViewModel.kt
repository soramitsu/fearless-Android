package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.sumBy
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorDetailsParcelToValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ValidatorDetailsViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val validator: ValidatorDetailsParcelModel,
    private val iconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ExternalAccountActions.Presentation by externalAccountActions {

    private val validatorDetailsFlow = MutableStateFlow(validator)

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val validatorDetails = validatorDetailsFlow.combine(assetFlow) { validator, asset ->
        mapValidatorDetailsParcelToValidatorDetailsModel(validator, asset, iconGenerator)
    }.flowOn(Dispatchers.IO).asLiveData()

    private val _openEmailEvent = MutableLiveData<Event<String>>()
    val openEmailEvent: LiveData<Event<String>> = _openEmailEvent

    private val _totalStakeEvent = MutableLiveData<Event<ValidatorStakeBottomSheet.Payload>>()
    val totalStakeEvent: LiveData<Event<ValidatorStakeBottomSheet.Payload>> = _totalStakeEvent

    fun backClicked() {
        router.back()
    }

    fun totalStakeClicked() {
        val validatorStake = validator.stake ?: return
        viewModelScope.launch {
            val asset = assetFlow.first()
            val payload = calculatePayload(asset, validatorStake)
            _totalStakeEvent.value = Event(payload)
        }
    }

    private suspend fun calculatePayload(asset: Asset, validatorStake: ValidatorStakeParcelModel) = withContext(Dispatchers.Default) {
        val ownStake = asset.token.amountFromPlanks(validatorStake.ownStake)
        val ownStakeFormatted = ownStake.formatWithDefaultPrecision(asset.token.type)
        val ownStakeFiatFormatted = asset.token.fiatAmount(ownStake)?.formatAsCurrency()

        val nominatorsStakeValue = validatorStake.nominators.sumBy { it.value }
        val nominatorsStake = asset.token.amountFromPlanks(nominatorsStakeValue)
        val nominatorsStakeFormatted = nominatorsStake.formatWithDefaultPrecision(asset.token.type)
        val nominatorsStakeFiatFormatted = asset.token.fiatAmount(nominatorsStake)?.formatAsCurrency()

        val totalStake = asset.token.amountFromPlanks(validatorStake.totalStake)
        val totalStakeFormatted = totalStake.formatWithDefaultPrecision(asset.token.type)
        val totalStakeFiatFormatted = asset.token.fiatAmount(totalStake)?.formatAsCurrency()

        ValidatorStakeBottomSheet.Payload(
            resourceManager.getString(R.string.staking_validator_own_stake),
            ownStakeFormatted,
            ownStakeFiatFormatted,
            resourceManager.getString(R.string.staking_validator_nominators),
            nominatorsStakeFormatted,
            nominatorsStakeFiatFormatted,
            resourceManager.getString(R.string.staking_validator_total),
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

    fun accountActionsClicked() {
        val address = validatorDetails.value?.address ?: return
        externalAccountActions.showExternalActions(ExternalAccountActions.Payload(address, address.networkType()))
    }
}
