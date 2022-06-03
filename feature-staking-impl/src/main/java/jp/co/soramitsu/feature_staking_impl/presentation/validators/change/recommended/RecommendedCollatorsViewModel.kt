package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapCollatorToCollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setRecommendedCollators
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger

class RecommendedCollatorsViewModel(
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
        recommendationSettingsProviderFactory.create(router.currentStackEntryLifecycle).defaultSettings()
    }

    private val recommendedCollators = flow {
        val collatorRecommendator = collatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val collators = collatorRecommendator.recommendations(recommendedSettings())

        emit(collators)
    }.inBackground().share()

    val recommendedCollatorModels = recommendedCollators
        .map {
            convertToModels(it, tokenUseCase.currentToken())
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
                    elected = true,
                    minBond = collatorModel.collator.bond,
                    delegations = collatorModel.collator.delegationCount.toInt(),
                    totalStake = collatorModel.collator.totalCounted,
                    estimatedRewards = 123.123,
                ),
                IdentityParcelModel(
                    display = collatorModel.collator.identity?.display,
                    legal = collatorModel.collator.identity?.legal,
                    web = collatorModel.collator.identity?.web,
                    riot = collatorModel.collator.identity?.riot,
                    email = collatorModel.collator.identity?.email,
                    pgpFingerprint = collatorModel.collator.identity?.pgpFingerprint,
                    image = collatorModel.collator.identity?.image,
                    twitter = collatorModel.collator.identity?.twitter,
                ),
                collatorModel.collator.request.orEmpty(),
            )
        )
    }

    fun nextClicked() {
        viewModelScope.launch {
            sharedStateSetup.setRecommendedCollators(recommendedCollators.first())

            router.openConfirmStaking()
        }
    }

    private suspend fun convertToModels(
        collators: List<Collator>,
        token: Token
    ): List<CollatorModel> {
        val chain = interactor.getSelectedChain()

        return collators.map {
            mapCollatorToCollatorModel(chain, it, addressIconGenerator, token)
        }
    }

    private fun retractRecommended() = sharedStateSetup.mutate {
        if (it is ReadyToSubmit && it.payload.selectionMethod == SelectionMethod.RECOMMENDED) {
            it.previous()
        } else {
            it
        }
    }
}
