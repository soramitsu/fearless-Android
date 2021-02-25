package jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorModel
import kotlinx.coroutines.Dispatchers

private const val ICON_SIZE_DP = 24

class RecommendedValidatorsViewModel(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val appLinksProvider: AppLinksProvider,
    private val interactor: StakingInteractor
) : BaseViewModel(), Browserable {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    val recommendedValidators = liveData(Dispatchers.Default) {
        val validatorRecommendator = validatorRecommendatorFactory.create()
        val validators = validatorRecommendator.recommendations(recommendedSettings())

        val networkType = interactor.getSelectedNetworkType()

        emit(convertToModels(validators, networkType))
    }

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        // TODO
    }

    fun learnMoreClicked() {
        openBrowserEvent.value = Event(appLinksProvider.stakingLearnMore)
    }

    private suspend fun convertToModels(
        validators: List<Validator>,
        networkType: Node.NetworkType
    ): List<ValidatorModel> {
        return validators.map {
            val address = it.accountIdHex.fromHex().toAddress(networkType)
            val addressModel = createAddressModel(address)

            mapValidatorToValidatorModel(it, addressModel)
        }
    }

    private suspend fun createAddressModel(address: String): AddressModel {
        return addressIconGenerator.createAddressModel(address, ICON_SIZE_DP)
    }

    private suspend fun recommendedSettings(): RecommendationSettings {
        return recommendationSettingsProviderFactory.get().defaultSettings()
    }
}