package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.castOrNull
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapCollatorToCollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setRecommendedCollators
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendedCollatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val collatorRecommendatorFactory: CollatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    private val stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    private val tokenUseCase: TokenUseCase,
) : BaseViewModel() {

    private val recommendedSettings by lazyAsync {
        recommendationSettingsProviderFactory.createParachain(router.currentStackEntryLifecycle).defaultSettings()
    }

    private val selectedCollator = MutableStateFlow<Collator?>(null)

    private val recommendedCollators = sharedStateSetup.setupStakingProcess.map {
        val userInputAmount = it.castOrNull<SetupStakingProcess.SelectBlockProducersStep.Collators>()
            ?.payload
            ?.castOrNull<SetupStakingProcess.SelectBlockProducersStep.Payload.Full>()?.amount
            ?: BigDecimal.ZERO
        val collatorRecommendator = collatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val token = interactor.currentAssetFlow().first().token
        val collators = collatorRecommendator.suggestedCollators(token.planksFromAmount(userInputAmount))
        collators
    }.inBackground().share()

    val recommendedCollatorModels = combine(recommendedCollators, selectedCollator) { collators, selected ->
        convertToModels(collators, tokenUseCase.currentToken(), selected?.address)
            .sortedByDescending { it.scoring?.apr }
    }.inBackground().share()

    val selectedTitle = recommendedCollators.map {
        val maxValidators = stakingParachainScenarioInteractor.maxDelegationsPerDelegator()

        resourceManager.getString(R.string.staking_custom_header_validators_title, it.size, maxValidators)
    }.inBackground().share()

    val toolbarTitle = resourceManager.getString(R.string.staking_suggested_collators_title)

    fun backClicked() {
//        retractRecommended()

        router.back()
    }

    fun collatorInfoClicked(collatorModel: CollatorModel) {
        router.openCollatorDetails(
            CollatorDetailsParcelModel(
                collatorModel.accountIdHex,
                CollatorStakeParcelModel(
                    status = collatorModel.collator.status,
                    selfBonded = collatorModel.collator.bond,
                    delegations = collatorModel.collator.delegationCount.toInt(),
                    totalStake = collatorModel.collator.totalCounted,
                    minBond = collatorModel.collator.lowestTopDelegationAmount,
                    estimatedRewards = collatorModel.collator.apy,
                ),
                collatorModel.collator.identity?.let {
                    IdentityParcelModel(
                        display = it.display,
                        legal = it.legal,
                        web = it.web,
                        riot = it.riot,
                        email = it.email,
                        pgpFingerprint = it.pgpFingerprint,
                        image = it.image,
                        twitter = it.twitter,
                    )
                },
                collatorModel.collator.request.orEmpty(),
            )
        )
    }

    fun collatorClicked(collator: CollatorModel) {
        selectedCollator.value = collator.collator
    }

    fun nextClicked() {
        viewModelScope.launch {
            sharedStateSetup.setRecommendedCollators(selectedCollator.value?.let { listOf(it) } ?: emptyList())

            router.openConfirmStaking()
        }
    }

    private suspend fun convertToModels(
        collators: List<Collator>,
        token: Token,
        selectedCollator: String?,
    ): List<CollatorModel> {
        return collators.map {
            mapCollatorToCollatorModel(
                collator = it,
                iconGenerator = addressIconGenerator,
                token = token,
                selectedCollatorAddress = selectedCollator,
                sorting = BlockProducersSorting.CollatorSorting.APYSorting
            )
        }
    }

    private fun retractRecommended() = sharedStateSetup.mutate {
        if (it is ReadyToSubmit<*> && it.payload.selectionMethod == SelectionMethod.RECOMMENDED) {
            it.previous()
        } else {
            it
        }
    }
}
